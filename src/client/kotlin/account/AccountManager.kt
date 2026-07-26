package org.lain.engine.client.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lain.engine.mc.commands.FriendlyException
import org.lain.engine.mc.server.HttpStatusException
import org.lain.engine.mc.server.RefreshToken
import org.lain.engine.mc.server.SessionTicket
import org.lain.engine.player.account.AccountResponse
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndUpdate

sealed interface ConnectionState {
    data object Unauthorized : ConnectionState
    data object Authorizing : ConnectionState
    data class Authorized(val account: ClientAuthorizedAccount) : ConnectionState
}

class NotAuthorizedException : FriendlyException("Вы не авторизованы")

class AccountManager(
    private val skinTextureManager: SkinTextureManager,
    private val httpClient: ClientEngineAccountService,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var lastAccountResponse: AccountResponse? = AccountResponseCache.load()

    @Volatile
    var state: ConnectionState = ConnectionState.Unauthorized
        private set

    @Volatile
    private var authJob: Job? = null

    val authorizing: Boolean
        get() = state is ConnectionState.Authorizing && authJob?.isActive == true

    val authorized: Boolean
        get() = state is ConnectionState.Authorized

    fun requireAccountResponse() = lastAccountResponse ?: throw NotAuthorizedException()

    suspend fun <T> sessionTicketOperation(authorized: ClientAuthorizedAccount, statement: suspend (SessionTicket) -> T) = withContext<T>(Dispatchers.IO) {
        val ticket = authorized.getSessionTicket().map()
        val result = statement(ticket)
        authorized.revokeSessionTicket(ticket.hash)
        result
    }

    suspend fun getAvailableAccountResponse(): AccountResponse {
        return runCatching { getAuthorized()?.getAccount() }
            .onFailure { it.printStackTrace() }
            .getOrNull()
            ?: requireAccountResponse()
    }

    fun getAccountResponseUpdateLaunching(): AccountResponse {
        return requireAccountResponse()
            .also {
                scope.launch { getAuthorized()?.getAccount() }
            }
    }

    suspend fun requireAuthorized() = getAuthorized() ?: throw NotAuthorizedException()

    suspend fun getAuthorized(): ClientAuthorizedAccount? {
        val authorizedState = state as? ConnectionState.Authorized
        val refreshTime = authorizedState?.account?.expireTime?.minus(Duration.ofSeconds(30L))
        return if (authorizedState != null && refreshTime?.isAfter(Instant.now()) == true) {
            authorizedState.account
        } else {
            val refreshToken = authorizedState?.account?.tokens?.refreshToken ?: loadRefreshTokenFromDrive()?.get()
            val authorized = runCatching {
                httpClient.authorizeRefreshToken(refreshToken ?: return null)
            }.getOrElse {
                state = ConnectionState.Unauthorized
                return null
            }
            onAuthorized(authorized)
            authorized
        }
    }

    private suspend fun onAuthorized(account: ClientAuthorizedAccount) {
        state = ConnectionState.Authorized(account)
        runCatching {
            val response = account.getAccount()
            lastAccountResponse = response
            response.characters
                .flatMap { it.looks }
                .forEach { skinTextureManager.preload(it) }
            AccountResponseCache.save(response)
        }
    }

    suspend fun authorizeDiscordOAuth2(): Job {
        val previousJob = mutex.withLock {
            authJob?.takeIf { it.isActive }?.also { it.cancel() }
        }

        previousJob?.join()

        return mutex.withLock {
            scope.launch {
                var authorization: ClientEngineAccountService.OAuth2Authorization? = null
                try {
                    authorization = httpClient.authorizeDiscordOAuth2()
                    val authorized = authorization.await()
                    onAuthorized(authorized)
                } catch (e: CancellationException) {
                    authorization?.abort()
                    state = ConnectionState.Unauthorized
                    throw e
                }
            }.also {
                authJob = it
                state = ConnectionState.Authorizing
            }
        }
    }

    fun autoLoginAsync() {
        scope.launch {
            mutex.withLock {
                if (state is ConnectionState.Authorized || authJob?.isActive == true) {
                    return@launch
                }

                loadRefreshTokenFromDrive()?.let { token ->
                    authJob = runRepeatingLoginJob(token)
                    state = ConnectionState.Authorizing
                }
            }
        }
    }

    private fun runRepeatingLoginJob(token: RefreshToken): Job = scope.launch {
        while (isActive) {
            try {
                val authorized = httpClient.authorizeRefreshToken(token.get())
                onAuthorized(authorized)
                return@launch
            } catch (e: HttpStatusException) {
                state = ConnectionState.Unauthorized
                throw e
            } catch (e: CancellationException) {
                state = ConnectionState.Unauthorized
                throw e
            } catch (e: Exception) {
                state = ConnectionState.Unauthorized
                delay(Duration.ofSeconds(1).toMillis())
            }
        }
    }
}
