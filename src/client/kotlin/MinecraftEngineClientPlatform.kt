package org.lain.engine.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import org.lain.engine.client.mc.ClientMinecraftAccess
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.blockHitResult
import org.lain.engine.client.mc.chat.MinecraftChat
import org.lain.engine.client.mc.updateEngineItemGroupEntries
import org.lain.engine.client.render.EnginePlayerSkin
import org.lain.engine.client.render.ui.EntityDebugScreen
import org.lain.engine.client.render.ui.Workspace
import org.lain.engine.client.render.world.DecalSystem
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.MinecraftAccessRegistry
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.mc.engineId
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.mc.voxelPos
import org.lain.engine.player.*
import org.lain.engine.script.EntityDebugData
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.VoxelPos

class MinecraftEngineClientPlatform(
    private val engineMinecraftClient: EngineMinecraftClient,
    private val minecraft: Minecraft,
    private val decalSystem: DecalSystem,
    override val modIds: List<String> = engineMinecraftClient.fabricLoader.allMods.map { it.metadata.id },
    override val tickExtension: ClientPlatform.TickExtension = engineMinecraftClient
) : ClientPlatform {
    var acousticDebugVolumesBlockPosCache = listOf<Pair<BlockPos, Float>>()
        private set

    private fun EnginePlayer.setMinecraftPlayerComponent(entity: Player) {
        set(MinecraftPlayer(entity))
        set(EnginePlayerSkin())
    }

    override fun disconnect(reason: String) {
        engineMinecraftClient.disconnectWithReason(DisconnectText(reason))
    }

    override fun createIntegratedServerPlayerLoadSettings(
        client: EngineClient,
        server: EngineServer,
    ): PlayerLoadSettings {
        val entity = MinecraftClient.player!!
        return EngineMinecraftServer.serverMinecraftPlayerLoadSettings(
            server,
            entity,
            entity.engineId,
            DeveloperModeStatus(client.developerMode, client.acousticDebug),
            listOf()
        )
    }

    override suspend fun onFullPlayerData(
        gameSession: GameSession,
        player: EnginePlayer,
        data: FullPlayerData
    ) = withClientContext {
        while (true) {
            val entity = minecraft.level?.players()?.firstOrNull { it.uuid == player.id.value }
            if (entity == null) {
                it.waitNextTick()
            } else {
                player.setMinecraftPlayerComponent(entity)
                return@withClientContext
            }
        }
    }

    override fun onPlayerDestroy(
        client: EngineClient,
        playerId: PlayerId
    ) {
        MinecraftChat.typingPlayers.removeIf { it.id == playerId }
    }

    override fun onMainPlayerInstantiated(
        client: EngineClient,
        gameSession: GameSession,
        player: EnginePlayer
    ) {
        val level = minecraft.level!!
        player.setMinecraftPlayerComponent(minecraft.player!!)
        ClientMixin.onMainPlayerInstantiated(player)
        MinecraftAccessRegistry.register(level, ClientMinecraftAccess(engineMinecraftClient, gameSession))
    }

    override fun onAcousticDebugVolumes(
        volumes: List<Pair<VoxelPos, Float>>,
        gameSession: GameSession
    ) {
        acousticDebugVolumesBlockPosCache =
            volumes.map { (pos, volume) -> BlockPos(pos.x, pos.y, pos.z) to volume }
    }

    override fun onCompiled(gameSession: GameSession) {
        updateEngineItemGroupEntries(gameSession)
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

    override fun onWorkspaceMenuOpen(gameSession: GameSession) {
        MinecraftClient.setScreen(
            Workspace(gameSession, gameSession.workspaceSavedState)
        )
    }

    override fun getHitResultVoxelPos(): VoxelPos? {
        return minecraft.blockHitResult?.blockPos?.voxelPos()
    }
}
