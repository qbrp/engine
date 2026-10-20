package org.lain.engine.transport.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.lain.engine.mc.DisconnectText
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.server.NetworkProtocolException
import org.lain.engine.transport.packet.VerificationResponsePacket

internal class ConnectionAuthorization(
    private val response: CompletableDeferred<VerificationResponsePacket>,
    private val job: Job,
) {
    fun complete(packet: VerificationResponsePacket): Boolean = response.complete(packet)

    fun cancel() {
        job.cancel()
    }
}

data class ConnectionSession(
    val username: Username,
    val playerId: PlayerId,
    val connection: ServerGamePacketListenerImpl,
    var player: EnginePlayer? = null
) {
    internal var authorization: ConnectionAuthorization? = null
}

typealias MinecraftConnection = ServerGamePacketListenerImpl

class ServerConnectionManager {
    private val byConnection: MutableMap<MinecraftConnection, ConnectionSession> = mutableMapOf()
    private val byId: MutableMap<PlayerId, ConnectionSession> = mutableMapOf()

    fun iterateSessions(): Iterable<ConnectionSession> = byConnection.values

    // возвращает, нужно ли принять игрока
    fun addConnectionSession(session: ConnectionSession): Boolean {
        val confirmed = byId.putIfAbsent(session.playerId, session) == null
        if (confirmed) {
            byConnection[session.connection] = session
        }
        return confirmed
    }

    fun removeConnectionSession(connection: MinecraftConnection): ConnectionSession? {
        val session = byConnection.remove(connection) ?: return null
        check(byId.remove(session.playerId) === session) {
            "Connection indexes are inconsistent for player ${session.playerId}"
        }
        return session
    }

    fun getSession(playerId: PlayerId): ConnectionSession {
        return byId[playerId] ?: error("Session $playerId not found")
    }

    fun getSessionOrNull(playerId: PlayerId): ConnectionSession? = byId[playerId]

    fun getSessionOrNull(connection: MinecraftConnection): ConnectionSession? = byConnection[connection]

    fun disconnect(connectionSession: ConnectionSession, reason: String) {
        connectionSession.connection.disconnect(DisconnectText(reason))
    }

    fun disconnect(connectionSession: ConnectionSession, exception: Throwable) {
        disconnect(connectionSession, exception.disconnectMessage())
    }

    private fun Throwable.disconnectMessage() = when (this) {
        is NetworkProtocolException -> "Рассинхронизация: ${message!!}.<newline>Перезайдите в игру. В случае, если ошибка продолжает появляться, свяжитесь с администраторами"
        else -> message ?: "Неизвестная ошибка"
    }
}
