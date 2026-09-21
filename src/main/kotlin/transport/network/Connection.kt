package org.lain.engine.transport.network

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.commands.FriendlyException
import org.lain.engine.mc.getPlayer
import org.lain.engine.mc.ecs.minecraftEntity
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerNotFoundException
import org.lain.engine.player.Username
import org.lain.engine.server.NetworkProtocolException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@JvmInline
value class SessionId(val id: UUID)

data class ConnectionSession(
    val uuid: SessionId,
    val username: Username,
    val playerId: PlayerId,
    val isOp: Boolean,
    val connection: ServerGamePacketListenerImpl,
    var mods: Set<String> = emptySet(),
    var player: EnginePlayer? = null
)

class ServerConnectionManager(
    private val minecraftServer: MinecraftServer,
) {
    private val sessions: ConcurrentHashMap<PlayerId, ConnectionSession> = ConcurrentHashMap()

    fun iterateSessions(): Iterable<ConnectionSession> = sessions.values

    fun addConnectionSession(session: ConnectionSession) {
        sessions[session.playerId] = session
    }

    fun removeConnectionSession(playerId: PlayerId) {
        sessions.remove(playerId)
    }

    fun getSession(playerId: PlayerId): ConnectionSession {
        return sessions[playerId] ?: error("Session $playerId not found")
    }

    fun getSessionOrNull(playerId: PlayerId): ConnectionSession? = sessions[playerId]

    fun disconnect(connectionSession: ConnectionSession, reason: String)  {
        val playerId = connectionSession.playerId
        val entity = connectionSession.player?.minecraftEntity as? ServerPlayer
            ?: minecraftServer.getPlayer(playerId)
            ?: throw PlayerNotFoundException(playerId)
        val networkHandler = entity.connection
        networkHandler.disconnect(DisconnectText(reason))
    }

    fun disconnect(playerId: PlayerId, exception: Throwable) {
        val message = when(exception) {
            is NetworkProtocolException -> "Рассинхронизация: ${exception.message!!}.<newline>Перезайдите в игру. В случае, если ошибка продолжает появляться, свяжитесь с администраторами"
            else -> exception.message ?: "Неизвестная ошибка"
        }
        disconnect(getSession(playerId), message)
        if (exception !is FriendlyException) {
            exception.printStackTrace()
        }
    }
}
