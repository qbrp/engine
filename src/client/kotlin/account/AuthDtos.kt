package org.lain.engine.client.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.mc.server.RefreshToken

@Serializable
data class ExchangeCodeRequest(
    val code: String,
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

@Serializable
data class RevokeSessionTicketRequest(
    val sessionTicket: String,
)


@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    @SerialName("expires_at")
    val expiresAt: Long,
) : RefreshToken {
    override fun get(): String = refreshToken
}
