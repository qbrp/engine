package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.InteractionSelection
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
// if null, reloads everything
data class ScriptsRecompileEndpoint(val scope: String? = null) : Packet

val CLIENTBOUND_SCRIPT_RECOMPILE_ENDPOINT = Endpoint<ScriptsRecompileEndpoint>()

// Controls

@Serializable
data class PlayerInteractionPacket(val playerId: PlayerId, val interaction: InteractionDto) : Packet

val CLIENTBOUND_PLAYER_INTERACTION_PACKET = Endpoint<PlayerInteractionPacket>()

@Serializable
data class PlayerInputPacket(val playerId: PlayerId, val actions: Set<InputActionDto>) : Packet

val CLIENTBOUND_PLAYER_INPUT_PACKET = Endpoint<PlayerInputPacket>()

@Serializable
data class InteractionSelectionPacket(val selection: InteractionSelection) : Packet

val CLIENTBOUND_INTERACTION_SELECTION_ENDPOINT = Endpoint<InteractionSelectionPacket>()

@Serializable
data class PlayerInteractionSelectionSelectPacket(val player: PlayerId, val variantId: String?) : Packet

val CLIENTBOUND_PLAYER_INTERACTION_SELECTION_SELECT_ENDPOINT = Endpoint<PlayerInteractionSelectionSelectPacket>()