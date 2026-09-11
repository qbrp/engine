package org.lain.engine.client

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerLoadSettings
import org.lain.engine.script.EntityDebugData
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.VoxelPos

interface ClientPlatform {
    val tickExtension: TickExtension
    val modIds: List<String>
    suspend fun onFullPlayerData(gameSession: GameSession, player: EnginePlayer, data: FullPlayerData)
    fun onPlayerDestroy(client: EngineClient, playerId: PlayerId)
    fun onMainPlayerInstantiated(client: EngineClient, gameSession: GameSession, player: EnginePlayer)
    fun onAcousticDebugVolumes(volumes: List<Pair<VoxelPos, Float>>, gameSession: GameSession)
    fun onCompiled(gameSession: GameSession)
    fun onChunkLoad(pos: EngineChunkPos, chunk: EngineChunk)
    fun onEntityDebugView(gameSession: GameSession)
    fun onEntityDebugViewData(data: EntityDebugData.Dto)
    fun onWorkspaceMenuOpen(gameSession: GameSession)
    fun getHitResultVoxelPos(): VoxelPos?
    fun disconnect(reason: String)
    fun createIntegratedServerPlayerLoadSettings(
        client: EngineClient,
        server: EngineServer,
    ): PlayerLoadSettings


    interface TickExtension {
        fun GameSession.tickDataPrepareSystem()
        fun GameSession.tickDataApplySystem()
        fun GameSession.tickBulletFireSystem()
    }
}
