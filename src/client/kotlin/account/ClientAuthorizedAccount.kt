package org.lain.engine.client.account

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
    Instant.now().plusSeconds(tokens.expiresIn)
) {
    init {
        RefreshTokenStorage.save(tokens.refreshToken)
    }
}