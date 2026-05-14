package org.lain.engine.transport.network

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.commands.FriendlyException
import org.lain.engine.mc.getPlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.Username
import org.lain.engine.server.DesynchronizationException
import java.util.*

@JvmInline
value class SessionId(val id: UUID)

data class ConnectionSession(
    val uuid: SessionId,
    val username: Username,
    val playerId: PlayerId,
    val isOp: Boolean,
    var mods: Set<String> = emptySet(),
)

class ServerConnectionManager(
    private val serverPlayerStorage: PlayerStorage,
    private val minecraftServer: MinecraftServer,
    private val entityTable: EntityTable
) {
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
        val playerId = connectionSession.playerId
        val entity = entityTable.server.getEntity(playerId) as? ServerPlayer ?: minecraftServer.getPlayer(playerId) ?: error("$playerId player not found")
        val networkHandler = entity.connection
        networkHandler.disconnect(DisconnectText(reason))
    }

    fun disconnect(playerId: PlayerId, exception: Throwable) {
        val message = when(exception) {
            is DesynchronizationException -> "Рассинхронизация: ${exception.message!!}.<newline>Перезайдите в игру. В случае, если ошибка продолжает появляться, свяжитесь с администраторами"
            else -> exception.message ?: "Неизвестная ошибка"
        }
        disconnect(getSession(playerId), message)
        if (exception !is FriendlyException) {
            exception.printStackTrace()
        }
    }
}