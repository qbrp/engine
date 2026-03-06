package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.SoundPlay
import org.lain.engine.mc.BlockHint
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.world.*

@Serializable
data class SoundPlayPacket(
    val play: SoundPlay,
    val context: SoundContext? = null,
) : Packet

val CLIENTBOUND_SOUND_PLAY_ENDPOINT = Endpoint<SoundPlayPacket>()

@Serializable
data class EngineChunkPacket(
    val pos: EngineChunkPos,
    val decals: Map<ImmutableVoxelPos, BlockDecals>,
    val hints: Map<ImmutableVoxelPos, BlockHint>
) : Packet

val CLIENTBOUND_CHUNK_ENDPOINT = Endpoint<EngineChunkPacket>()

@Serializable
data class VoxelEventPacket(val event: VoxelEvent) : Packet

val CLIENTBOUND_VOXEL_EVENT_PACKET = Endpoint<VoxelEventPacket>()