package org.lain.engine.server.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.player.account.CharacterDataResponse
import org.lain.engine.player.account.SessionTicketDto
import java.time.Instant

open class AuthorizedAccount(
    protected val httpClient: EngineHttpClient,
    val bearerToken: String,
    val expireTime: Instant
) {
    protected fun checkValid() {
        check(Instant.now().isBefore(expireTime)) {
            "Backend session expired"
        }
    }

    suspend fun getCharacter(id: String): CharacterDataResponse = withContext(Dispatchers.IO) {
        checkValid()
        httpClient.getJson("characters/$id", bearerToken)
    }

    suspend fun getAccount(): AccountResponse = withContext(Dispatchers.IO) {
        checkValid()
        httpClient.getJson("/me", bearerToken = bearerToken)
    }

    suspend fun getSessionTicket(): SessionTicketDto = withContext(Dispatchers.IO) {
        checkValid()
        httpClient.postJson<Unit, SessionTicketDto>("/auth/session-ticket", bearerToken = bearerToken)
    }
}