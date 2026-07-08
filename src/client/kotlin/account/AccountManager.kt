package org.lain.engine.client.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

sealed interface AccountState {
    data object Unauthorized : AccountState
    data object Authorizing : AccountState
    data object Failed : AccountState
    data class Authorized(val account: AccountResponse) : AccountState
}

class AccountManager(
    private val httpClient: EngineHttpClient,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var state: AccountState = AccountState.Unauthorized
        private set

    @Volatile
    private var authJob: Job? = null

    val account: AccountResponse?
        get() = (state as? AccountState.Authorized)?.account

    val authorizing: Boolean
        get() = state is AccountState.Authorizing && authJob?.isActive == true

    suspend fun authorizeDiscordOAuth2(): Job {
        val previousJob = mutex.withLock {
            authJob?.takeIf { it.isActive }?.also { it.cancel() }
        }

        previousJob?.join()

        return mutex.withLock {
            scope.launch {
                var authorization: EngineHttpClient.Authorization? = null
                try {
                    authorization = httpClient.authorizeDiscordOAuth2()
                    state = AccountState.Authorized(authorization.await().getAccountSummary())
                } catch (e: CancellationException) {
                    authorization?.abort()
                    state = AccountState.Unauthorized
                    throw e
                }
            }.also {
                authJob = it
                state = AccountState.Authorizing
            }
        }
    }

    fun autoLoginAsync() {
        scope.launch {
            mutex.withLock {
                if (state is AccountState.Authorized || authJob?.isActive == true) {
                    return@launch
                }

                loadRefreshTokenFromDrive()?.let { token ->
                    authJob = runRepeatingLoginJob(token)
                    state = AccountState.Authorizing
                }
            }
        }
    }

    private fun runRepeatingLoginJob(token: RefreshToken): Job = scope.launch {
        while (isActive) {
            try {
                state = AccountState.Authorized(httpClient.refresh(token).getAccountSummary())
                return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = AccountState.Failed
                delay(Duration.ofSeconds(1).toMillis())
                continue
            }
        }
    }
}
