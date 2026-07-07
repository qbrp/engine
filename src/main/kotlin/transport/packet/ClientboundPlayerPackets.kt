package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.interaction.InteractionSelection
import org.lain.engine.player.PlayerId
import org.lain.engine.server.Notification
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

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