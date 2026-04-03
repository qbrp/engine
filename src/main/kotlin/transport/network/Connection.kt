package org.lain.engine.transport.network

import org.lain.engine.mc.disconnectInternal
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.server.DesynchronizationException
import java.util.*

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
            reason
        )
    }

    fun disconnect(playerId: PlayerId, exception: Throwable) {
        val message = when(exception) {
            is DesynchronizationException -> "Рассинхронизация: ${exception.message!!}.<newline>Перезайдите в игру. В случае, если ошибка продолжает появляться, свяжитесь с администраторами"
            else -> exception.message ?: "Неизвестная ошибка"
        }
        disconnect(getSession(playerId), message)
        exception.printStackTrace()
    }
}