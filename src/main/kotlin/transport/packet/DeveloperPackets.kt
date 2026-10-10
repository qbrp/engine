package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.script.dev.EntityInspectionSnapshot
import org.lain.engine.script.dev.InspectionPrimitive
import org.lain.engine.data.PersistentId
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
data class EntityDebugDataPacket(val persistentId: PersistentId, val data: EntityInspectionSnapshot.Dto) : Packet

val CLIENTBOUND_ENTITY_DEBUG_DATA_ENDPOINT = Endpoint<EntityDebugDataPacket>()

@Serializable
data class EntityInspectionPacket(
    val persistentId: PersistentId,
    val rate: Int
) : Packet

val SERVERBOUND_ENTITY_DEBUG_VIEW_ENDPOINT = Endpoint<EntityInspectionPacket>()

@Serializable
data class EntityDebugEditPacket(val id: Int, val value: InspectionPrimitive) : Packet

val SERVERBOUND_ENTITY_DEBUG_EDIT_ENDPOINT = Endpoint<EntityDebugEditPacket>()

@Serializable
data class EntityDebugViewStopPacket(val entity: PersistentId) : Packet

val SERVERBOUND_ENTITY_DEBUG_VIEW_STOP_ENDPOINT = Endpoint<EntityDebugViewStopPacket>()
