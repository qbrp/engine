package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.SoundPlay
import org.lain.engine.mc.BlockHint
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.world.BlockDecals
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.ImmutableVoxelPos
import org.lain.engine.world.SoundContext

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
data class VoxelUpdatePacket(val voxelPos: ImmutableVoxelPos, val decals: BlockDecals?, val hint: BlockHint?) : Packet

val CLIENTBOUND_VOXEL_UPDATE_ENDPOINT = Endpoint<VoxelUpdatePacket>()

@Serializable
data class DestroyVoxelPacket(val voxelPos: ImmutableVoxelPos) : Packet

val CLIENTBOUND_VOXEL_DESTROY_ENDPOINT = Endpoint<DestroyVoxelPacket>()

