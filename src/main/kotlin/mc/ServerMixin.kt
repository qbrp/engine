package org.lain.engine.mc

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.minecraft.world.level.chunk.LevelChunk
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.canChangeGameMode
import org.lain.engine.server.Notification
import org.lain.engine.util.Injector

object ServerMixin {
    private val server get() = Injector.server
    private val serverHandler get() = server?.engine?.handler
    private val chat get() = server?.engine?.chat
    private val chatSettings get() = chat?.settings
    var vanillaDamageEnabled = false

    fun onProcessPackets() {
        serverHandler?.processHandlerTasks()
    }

    fun notifyPlayerGameModeChange(player: ServerPlayer, gameMode: GameType) {
        val enginePlayer = player.getEngineState() ?: return
        serverHandler?.onServerNotification(
            enginePlayer,
            when (gameMode) {
                GameType.SURVIVAL -> Notification.SURVIVAL_GAMEMODE
                GameType.CREATIVE -> Notification.CREATIVE_GAMEMODE
                GameType.ADVENTURE -> Notification.ADVENTURE_GAMEMODE
                GameType.SPECTATOR -> Notification.SPECTATOR_GAMEMODE
            },
            false
        )
    }

    fun allowGameModeChangeOrNotify(player: ServerPlayer): Boolean {
        val enginePlayer = player.getEngineState() ?: return false
        val result = enginePlayer.canChangeGameMode()
        if (!result) {
            serverHandler?.onServerNotification(enginePlayer, Notification.CHANGE_GAMEMODE_FORBIDDEN, false)
        }
        return result
    }

    fun onChunkDataSent(chunk: LevelChunk, player: ServerPlayer) = server?.let { server ->
        //TODO: потенциальный проёб с чанками
        val access = chunk.level.requireEngineAccess()
        val world = access.requireEngineWorld(chunk.level)
        val chunkStorage = world.chunkStorage
        val chunkPos = chunk.pos.engineChunkPos()
        val engineChunk = chunkStorage.requireChunk(chunkPos)
        server.engine.handler.sendChunkSnapshot(player.engineId, engineChunk, chunkPos)
    }

    fun shouldCancelSendJoinMessage() = chatSettings?.let { it.joinMessage != "" || !it.joinMessageEnabled } ?: true

    fun shouldCancelSendLeaveMessage() = chatSettings?.let { it.leaveMessage != "" || !it.leaveMessageEnabled } ?: true

    fun shouldCancelDamage() = !vanillaDamageEnabled

    fun getEnginePlayer(player: Player): EnginePlayer? = player.getEngineState()
}