package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.PlayerId
import org.lain.engine.server.Notification
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.world.ImmutableVoxelPos

// Custom Name

@Serializable
data class PlayerNotificationPacket(
    val type: Notification,
    val once: Boolean
) : Packet

val CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT = Endpoint<PlayerNotificationPacket>()

// Server Settings

@Serializable
data class ServerSettingsUpdatePacket(
    val settings: ClientboundServerSettings
) : Packet

val CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT = Endpoint<ServerSettingsUpdatePacket>()

// Contents update

@Serializable
object ContentsUpdatePacket : Packet

val CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT = Endpoint<ContentsUpdatePacket>()

// Acoustic debug

@Serializable
data class AcousticDebugVolumesPacket(val volumes: List<Pair<ImmutableVoxelPos, Float>>) : Packet

val CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET = Endpoint<AcousticDebugVolumesPacket>()

// Controls

@Serializable
data class PlayerInteractionPacket(val playerId: PlayerId, val interaction: InteractionDto) : Packet

val CLIENTBOUND_PLAYER_INTERACTION_PACKET = Endpoint<PlayerInteractionPacket>()

@Serializable
data class PlayerInputPacket(val playerId: PlayerId, val actions: Set<InputActionDto>) : Packet

val CLIENTBOUND_PLAYER_INPUT_PACKET = Endpoint<PlayerInputPacket>()