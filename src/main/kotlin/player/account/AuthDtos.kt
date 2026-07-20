package org.lain.engine.player.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.mc.server.SessionTicket
import java.time.Instant

@Serializable
data class SessionTicketDto(
    val sessionTicket: String,
    @SerialName("expires_at") val expiresAt: Long,
) {
    fun map() = SessionTicket(
        sessionTicket,
        Instant.ofEpochMilli(expiresAt)
    )
}