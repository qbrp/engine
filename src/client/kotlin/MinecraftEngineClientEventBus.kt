package org.lain.engine.client

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.Chunk
import org.lain.engine.client.mc.MinecraftChat
import org.lain.engine.client.mc.render.ChunkDecalsStorage
import org.lain.engine.client.mc.updateEngineItemGroupEntries
import org.lain.engine.item.ItemAccess
import org.lain.engine.mc.ClientPlayerTable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.util.Injector
import org.lain.engine.world.VoxelPos
import java.util.*

class MinecraftEngineClientEventBus(
    private val minecraft: MinecraftClient,
    private val table: ClientPlayerTable,
    private val decalsStorage: ChunkDecalsStorage,
    private val chunks: MutableList<Chunk>
) : ClientEventBus {
    private data class PendingFullPlayerData(val player: EnginePlayer, val data: FullPlayerData)
    private val pendingFullPlayerData: MutableList<PendingFullPlayerData> = LinkedList()
    var acousticDebugVolumesBlockPosCache = listOf<Pair<BlockPos, Float>>()
        private set

    override fun tick() {
        for (player in pendingFullPlayerData.toList()) {
            pendingFullPlayerData.remove(player)
            tryApplyFullPlayerData(player.player, player.data)
        }
    }

    override fun onFullPlayerData(
        client: EngineClient,
        id: PlayerId,
        data: FullPlayerData
    ) {
        val player = client.gameSession?.getPlayer(id) ?: error("Игрока $id для синхронизации состояния не существует")
        tryApplyFullPlayerData(player, data)
    }

    private fun tryApplyFullPlayerData(player: EnginePlayer, data: FullPlayerData) {
        val entity = minecraft.world?.players?.firstOrNull { it.uuid == player.id.value } ?: run {
            pendingFullPlayerData.add(PendingFullPlayerData(player, data))
            return
        }
        table.setPlayer(entity, player)
    }

    override fun onPlayerDestroy(
        client: EngineClient,
        playerId: PlayerId
    ) {
        table.removePlayer(playerId)
        MinecraftChat.typingPlayers.removeIf { it.id == playerId }
    }

    override fun onMainPlayerInstantiated(
        client: EngineClient,
        gameSession: GameSession,
        player: EnginePlayer
    ) {
        table.setPlayer(minecraft.player!!, player)
        Injector.register(gameSession.itemStorage)
        Injector.register(gameSession.movementSettings)
        if (!minecraft.isInSingleplayer) {
            Injector.register<ItemAccess>(gameSession.itemStorage)
        }
        chunks.forEach { decalsStorage.survey(it) }
    }

    override fun onAcousticDebugVolumes(volumes: List<Pair<VoxelPos, Float>>, gameSession: GameSession) {
        acousticDebugVolumesBlockPosCache = volumes.map { (pos, volume) -> BlockPos(pos.x, pos.y, pos.z) to volume }
    }

    override fun onContentsUpdate() {
        updateEngineItemGroupEntries()
    }
}