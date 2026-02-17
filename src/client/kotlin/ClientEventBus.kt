package org.lain.engine.client

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.VoxelPos

interface ClientEventBus {
    fun tick()
    fun onFullPlayerData(client: EngineClient, id: PlayerId, data: FullPlayerData)
    fun onPlayerDestroy(client: EngineClient, playerId: PlayerId)
    fun onMainPlayerInstantiated(client: EngineClient, gameSession: GameSession, player: EnginePlayer)
    fun onAcousticDebugVolumes(volumes: List<Pair<VoxelPos, Float>>, gameSession: GameSession)
    fun onContentsUpdate()
    fun onChunkLoad(pos: EngineChunkPos, chunk: EngineChunk)
}