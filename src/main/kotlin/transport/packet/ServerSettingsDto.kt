package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.*
import org.lain.engine.server.EngineServer

@Serializable
data class ClientboundServerSettings(
    val playerSynchronizationRadius: Int,
    val chat: ClientChatSettings,
    val movement: MovementSettings,
    val defaultAttributes: ClientDefaultAttributes
) {
    companion object {
        fun of (server: EngineServer, player: EnginePlayer): ClientboundServerSettings {
            return ClientboundServerSettings(
                server.globals.playerSynchronizationRadius,
                ClientChatSettings.of(server.globals.chatSettings, player),
                server.globals.movementSettings,
                ClientDefaultAttributes.of(server.globals.defaultPlayerAttributes),
            )
        }
    }
}

@Serializable
data class ClientDefaultAttributes(
    val movement: MovementDefaultAttributes,
    val maxVolume: Float,
    val baseVolume: Float,
    val gravity: Float,
) {
    companion object {
        fun of(defaults: DefaultPlayerAttributes): ClientDefaultAttributes {
            return ClientDefaultAttributes(
                defaults.movement,
                defaults.maxVolume,
                defaults.playerBaseInputVolume,
                defaults.gravity,
            )
        }
    }
}