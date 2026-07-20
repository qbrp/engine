package org.lain.engine.client.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lain.engine.mc.server.AuthorizedAccount
import org.lain.engine.mc.server.EngineHttpClient
import org.lain.engine.mc.server.RefreshToken
import java.time.Instant

class ClientAuthorizedAccount(
    httpClient: EngineHttpClient,
    val tokens: TokenResponse,
) : AuthorizedAccount(
    httpClient,
    tokens.accessToken,
    Instant.ofEpochMilli(tokens.expiresAt)
) {
    init {
        RefreshTokenStorage.save(tokens.refreshToken)
    }

    suspend fun revokeSessionTicket(ticketHash: String) = withContext(Dispatchers.IO) {
        checkValid()
        httpClient.postJson<RevokeSessionTicketRequest, String>(
            "/auth/session-ticket/revoke",
            body = RevokeSessionTicketRequest(ticketHash),
            bearerToken = bearerToken
        )
    }
}