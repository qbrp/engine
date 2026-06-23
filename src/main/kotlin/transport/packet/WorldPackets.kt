package org.lain.engine.transport.packet

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.lain.engine.storage.COMPONENT_CBOR
import org.lain.engine.storage.ComponentDto
import org.lain.engine.storage.EntityDto
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.world.*

@Serializable
data class SoundPlayPacket(
    val play: SoundPlay,
    val context: SoundContext? = null,
) : Packet

val CLIENTBOUND_SOUND_PLAY_ENDPOINT = Endpoint<SoundPlayPacket>()

@Serializable
data class EngineChunkPacket(val chunk: EngineChunkDto) : Packet

@Serializable
data class EngineChunkDto(
    val pos: EngineChunkPos,
    val decals: Map<ImmutableVoxelPos, BlockDecals>,
    val hints: Map<ImmutableVoxelPos, Hint>
)

val CLIENTBOUND_CHUNK_ENDPOINT = Endpoint<EngineChunkPacket>()

@Serializable
data class VoxelEventPacket(val event: VoxelEvent) : Packet

val CLIENTBOUND_VOXEL_EVENT_PACKET = Endpoint<VoxelEventPacket>()

@Serializable
data class VoxelBlockHintPacket(val pos: VoxelPos, val action: Action) : Packet {
    @Serializable
    sealed class Action {
        @Serializable
        data class Add(val text: String) : Action()
        @Serializable
        data class Remove(val index: Int) : Action()
    }
}

val SERVERBOUND_VOXEL_BLOCK_HINT_PACKET = Endpoint<VoxelBlockHintPacket>()

@Serializable
data class EntityDeltaPacket(val dto: EntityDto) : Packet

@OptIn(ExperimentalSerializationApi::class)
val CLIENTBOUND_ENTITY_DELTA_ENDPOINT = Endpoint<EntityDeltaPacket>(
    codec = PacketCodec.Kotlinx(EntityDeltaPacket.serializer(), COMPONENT_CBOR),
)

@Serializable
data class DynamicVoxelDeltaPacket(
    val voxelPos: ImmutableVoxelPos,
    val components: List<ComponentDto>
) : Packet

val CLIENTBOUND_DYNAMIC_VOXEL_DELTA_ENDPOINT = Endpoint<DynamicVoxelDeltaPacket>()

@Serializable
data class WorldStateDeltaPacket(val components: List<ComponentDto>) : Packet

@OptIn(ExperimentalSerializationApi::class)
val CLIENTBOUND_WORLD_STATE_DELTA_PACKET = Endpoint<WorldStateDeltaPacket>(
    codec = PacketCodec.Kotlinx(WorldStateDeltaPacket.serializer(), COMPONENT_CBOR),
)
