package org.lain.engine.mc.server

import org.lain.engine.player.account.SessionTicketDto
import java.time.Instant

data class SessionTicket(val hash: String, val expiresAt: Instant) {
    fun toDto() = SessionTicketDto(hash, expiresAt.toEpochMilli())
}