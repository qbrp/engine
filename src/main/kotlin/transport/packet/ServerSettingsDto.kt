package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.MovementDefaultAttributes
import org.lain.engine.player.MovementSettings
import org.lain.engine.player.Player
import org.lain.engine.player.playerBaseInputVolume
import org.lain.engine.server.EngineServer

@Serializable
data class ClientboundServerSettings(
    val playerSynchronizationRadius: Int,
    val chat: ClientChatSettings,
    val movement: MovementSettings,
    val defaultAttributes: ClientDefaultAttributes
) {
    companion object {
        fun of (server: EngineServer, player: Player): ClientboundServerSettings {
            return ClientboundServerSettings(
                server.globals.playerSynchronizationRadius,
                server.chat.settings.let {
                    ClientChatSettings.of(server.chat.settings, player)
                },
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
    val baseVolume: Float
) {
    companion object {
        fun of(defaults: DefaultPlayerAttributes): ClientDefaultAttributes {
            return ClientDefaultAttributes(
                defaults.movement,
                defaults.maxVolume,
                defaults.playerBaseInputVolume
            )
        }
    }
}