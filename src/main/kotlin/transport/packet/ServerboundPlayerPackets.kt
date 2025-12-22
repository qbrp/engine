package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

@Serializable
data class SetSpeedIntentionPacket(
    val value: Float
) : Packet

val SERVERBOUND_SPEED_INTENTION_PACKET = Endpoint<SetSpeedIntentionPacket>()

@Serializable
data class DeveloperModePacket(
    val enabled: Boolean
) : Packet

val SERVERBOUND_DEVELOPER_MODE_PACKET = Endpoint<DeveloperModePacket>()

@Serializable
data class VolumePacket(
    val volume: Float
) : Packet

val SERVERBOUND_VOLUME_PACKET = Endpoint<VolumePacket>()