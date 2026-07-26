package org.lain.engine.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.mc.ClientMixinAccess
import org.lain.engine.client.mc.IntegratedEngineMinecraftServer
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.blockHitResult
import org.lain.engine.client.mc.chat.MinecraftChat
import org.lain.engine.client.mc.updateEngineItemGroupEntries
import org.lain.engine.client.render.ui.EntityDebugScreen
import org.lain.engine.client.render.ui.Workspace
import org.lain.engine.client.render.world.DecalSystem
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.engineId
import org.lain.engine.mc.server.serverMinecraftPlayerLoadSettings
import org.lain.engine.mc.voxelPos
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.get
import org.lain.engine.script.EntityDebugData
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.util.Injector
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.VoxelPos
import java.util.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class MinecraftEngineClientInfrastructure(
    private val engineMinecraftClient: EngineMinecraftClient,
    private val minecraft: Minecraft,
    private val table: EntityTable,
    private val decalSystem: DecalSystem,
    override val modIds: List<String> = engineMinecraftClient.fabricLoader.allMods.map { it.metadata.id }
) : ClientInfrastructure {
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

    override fun disconnect(reason: String) {
        engineMinecraftClient.disconnectWithReason(DisconnectText(reason))
    }

    override fun createIntegratedServerPlayerLoadSettings(client: EngineClient, server: EngineServer): PlayerLoadSettings {
        val minecraftServer by injectMinecraftEngineServer()
        minecraftServer as IntegratedEngineMinecraftServer
        val entity = MinecraftClient.player!!
        return server.serverMinecraftPlayerLoadSettings(
            entity,
            entity.engineId,
            DeveloperModeStatus(client.developerMode, client.acousticDebug),
            listOf()
        )
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
        val entity = minecraft.level?.players()?.firstOrNull { it.uuid == player.id.value } ?: run {
            pendingFullPlayerData.add(PendingFullPlayerData(player, data))
            return
        }
        table.client.setPlayer(entity, player)
    }

    override fun onPlayerDestroy(
        client: EngineClient,
        playerId: PlayerId
    ) {
        table.client.removePlayer(playerId)
        MinecraftChat.typingPlayers.removeIf { it.id == playerId }
    }

    override fun onMainPlayerInstantiated(
        client: EngineClient,
        gameSession: GameSession,
        player: EnginePlayer
    ) {
        table.client.setPlayer(minecraft.player!!, player)
        Injector.register(gameSession.itemStorage)
        Injector.register(gameSession.movementSettings)
        ClientMixinAccess.onMainPlayerInstantiated(player)
    }

    override fun onAcousticDebugVolumes(volumes: List<Pair<VoxelPos, Float>>, gameSession: GameSession) {
        acousticDebugVolumesBlockPosCache = volumes.map { (pos, volume) -> BlockPos(pos.x, pos.y, pos.z) to volume }
    }

    override fun onContentsUpdate() {
        updateEngineItemGroupEntries()
    }

    override fun onChunkLoad(pos: EngineChunkPos, chunk: EngineChunk) {
        decalSystem.loadTextures(pos, chunk)
    }

    override fun onEntityDebugView(gameSession: GameSession) {
        minecraft.setScreen(EntityDebugScreen(gameSession.client))
    }

    override fun onEntityDebugViewData(data: EntityDebugData.Dto) {
        val screen = minecraft.screen
        if (screen !is EntityDebugScreen) return
        screen.applyEntityDebugData(data)
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun onCharacterSelectionMenuOpen(gameSession: GameSession) {
        val mainPlayer = gameSession.mainPlayer
        val appliedCharacter = mainPlayer.get<AppliedCharacter>()?.character
        val selectedLook = mainPlayer.get<SelectedLook>()?.look
        val currentPlayerCharacter = if (appliedCharacter != null && selectedLook != null) {
            ClientHandler.CurrentPlayerCharacter(appliedCharacter, selectedLook)
        } else {
            null
        }
        gameSession.client.handler.selectCharacter(minecraft.isSingleplayer, currentPlayerCharacter)
    }

    override fun onWorkspaceMenuOpen(gameSession: GameSession) {
        MinecraftClient.setScreen(
            Workspace(gameSession, gameSession.workspaceSavedState)
        )
    }

    override fun getHitResultVoxelPos(): VoxelPos? {
        return minecraft.blockHitResult?.blockPos?.voxelPos()
    }
}