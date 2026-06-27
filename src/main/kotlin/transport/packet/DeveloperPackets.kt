package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.EntityDebugData
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.world.ImmutableVoxelPos

@Serializable
data class DeveloperModePacket(val status: DeveloperModeStatus) : Packet

@Serializable
data class DeveloperModeStatus(val enabled: Boolean = false, val acoustic: Boolean = false)

val SERVERBOUND_DEVELOPER_MODE_PACKET = Endpoint<DeveloperModePacket>()

// Acoustic debug

@Serializable
data class AcousticDebugVolumesPacket(val volumes: List<Pair<ImmutableVoxelPos, Float>>) : Packet

val CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET = Endpoint<AcousticDebugVolumesPacket>()

// Entity Debug

@Serializable
data class EntityDebugDataPacket(val data: EntityDebugData) : Packet

val CLIENTBOUND_ENTITY_DEBUG_DATA_ENDPOINT = Endpoint<EntityDebugDataPacket>()

@Serializable
data class EntityDebugViewPacket(val persistentId: PersistentId) : Packet

val SERVERBOUND_ENTITY_DEBUG_VIEW_ENDPOINT = Endpoint<EntityDebugViewPacket>()

@Serializable
object EntityDebugViewStopPacket : Packet

val SERVERBOUND_ENTITY_DEBUG_VIEW_STOP_ENDPOINT = Endpoint<EntityDebugViewStopPacket>()