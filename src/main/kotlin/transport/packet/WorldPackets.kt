package org.lain.engine.transport.packet

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.lain.engine.server.EntityNetworkSnapshot
import org.lain.engine.server.ReplicationFrameSnapshot
import org.lain.engine.storage.COMPONENT_CBOR
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.world.*

@Serializable
data class SoundPlayPacket(
    val play: SoundPlay,
    val ignorePhysics: Boolean
) : Packet

val CLIENTBOUND_SOUND_PLAY_ENDPOINT = Endpoint<SoundPlayPacket>()

@Serializable
data class EngineChunkPacket(val chunk: EngineChunkDto) : Packet {
    override val requireAuthorized: Boolean = false
}

@Serializable
data class EngineChunkDto(
    val pos: EngineChunkPos,
    val decals: Map<ImmutableVoxelPos, BlockDecals>,
    val hints: Map<ImmutableVoxelPos, Hint>
) {
    fun isEmpty() = decals.isEmpty() && hints.isEmpty()
}

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
data class ReplicationPacket(val frame: ReplicationFrameSnapshot) : Packet

@OptIn(ExperimentalSerializationApi::class)
val CLIENTBOUND_REPLICATION_ENDPOINT = Endpoint<ReplicationPacket>(
    codec = PacketCodec.Kotlinx(ReplicationPacket.serializer(), COMPONENT_CBOR),
)

@Serializable
data class PlayerInputProcessedPacket(
    val processedInputTick: Long,
) : Packet

val CLIENTBOUND_PLAYER_INPUT_PROCESSED_ENDPOINT = Endpoint<PlayerInputProcessedPacket>()

@Serializable
data class EntityResyncRequestPacket(val persistentId: PersistentId) : Packet

val SERVERBOUND_ENTITY_RESYNC_REQUEST_ENDPOINT = Endpoint<EntityResyncRequestPacket>()

@Serializable
data class WorldStateDeltaPacket(
    val snapshot: EntityNetworkSnapshot
) : Packet

@OptIn(ExperimentalSerializationApi::class)
val CLIENTBOUND_WORLD_STATE_DELTA_PACKET = Endpoint<WorldStateDeltaPacket>(
    codec = PacketCodec.Kotlinx(WorldStateDeltaPacket.serializer(), COMPONENT_CBOR),
)
