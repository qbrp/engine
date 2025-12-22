package org.lain.engine.transport.network

import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.disconnectInternal
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.util.engineId
import org.lain.engine.util.parseMiniMessage
import java.util.UUID

fun DisconnectText(reason: String) = "<red>[ENGINE] ${reason}</red>"

@JvmInline
value class SessionId(val id: UUID)

data class ConnectionSession(
    val uuid: SessionId,
    val username: Username,
    val playerId: PlayerId,
    val isOp: Boolean
)

class ServerConnectionManager {
    private val sessions: MutableMap<PlayerId, ConnectionSession> = mutableMapOf()

    fun addConnectionSession(session: ConnectionSession) {
        sessions[session.playerId] = session
    }

    fun removeConnectionSession(playerId: PlayerId) {
        sessions.remove(playerId)
    }

    fun getSession(playerId: PlayerId): ConnectionSession {
        return sessions[playerId] ?: error("Session $playerId not found")
    }

    fun disconnect(connectionSession: ConnectionSession, reason: String)  {
        disconnectInternal(
            connectionSession.playerId,
            DisconnectText(reason)
        )
    }
}