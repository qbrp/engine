package org.lain.engine.transport.packet

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.lain.engine.storage.COMPONENT_SERIALIZERS_MODULE
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.util.component.Component
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
data class EntityPacket(val persistentId: PersistentId, val components: List<Component>) : Packet

@OptIn(ExperimentalSerializationApi::class)
val CLIENTBOUND_ENTITY_ENDPOINT = Endpoint<EntityPacket>(
    codec = PacketCodec.Kotlinx(EntityPacket.serializer(), ProtoBuf { serializersModule = COMPONENT_SERIALIZERS_MODULE }),
)