package org.lain.engine.client

import net.minecraft.client.MinecraftClient
import org.lain.engine.mc.ClientPlayerTable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.util.Injector
import java.util.LinkedList

class MinecraftEngineClientEventBus(
    private val minecraft: MinecraftClient,
    private val table: ClientPlayerTable
) : ClientEventBus {
    private data class PendingFullPlayerData(val player: EnginePlayer, val data: FullPlayerData)
    private val pendingFullPlayerData: MutableList<PendingFullPlayerData> = LinkedList()

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
    }

    override fun onMainPlayerInstantiated(
        client: EngineClient,
        gameSession: GameSession,
        player: EnginePlayer
    ) {
        table.setPlayer(minecraft.player!!, player)
        Injector.register(gameSession.itemStorage)
    }
}