package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.chat.Selector
import org.lain.engine.chat.isChannelAvailable
import org.lain.engine.player.Player
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.player.PlayerId
import org.lain.engine.server.EngineServer
import org.lain.engine.util.ImmutableVec3
import org.lain.engine.world.WorldId

@Serializable
data class OutcomingChatMessagePacket(
    val sourcePosition: ImmutableVec3?,
    val sourceWorld: WorldId,
    val sourcePlayer: PlayerId?,
    val sourceAuthorName: String,
    val text: String,
    val channel: ChannelId,
    val mentioned: Boolean,
    val speech: Boolean,
    val volume: Float?,
    val isSpy: Boolean,
    val placeholders: Map<String, String>
) : Packet

val CLIENTBOUND_CHAT_MESSAGE_ENDPOINT = Endpoint<OutcomingChatMessagePacket>()

@Serializable
data class IncomingChatMessagePacket(
    val text: String,
    val channel: ChannelId
) : Packet

val SERVERBOUND_CHAT_MESSAGE_ENDPOINT = Endpoint<IncomingChatMessagePacket>()

@Serializable
data class ChatSettingsPacket(
    val settings: ClientChatSettings
) : Packet

@Serializable
data class ClientChatSettings(
    val channels: Map<ChannelId, ClientChatChannel>,
    val default: ChannelId,
    val placeholders: Map<String, String>,
) {
    companion object {
        fun of(settings: EngineChatSettings, player: Player): ClientChatSettings {
            return ClientChatSettings(
                settings.channels.associate {
                    it.id to ClientChatChannel.of(it, player)
                },
                settings.defaultChannel.id,
                settings.placeholders,
            )
        }
    }
}

@Serializable
data class ClientChatChannel(
    val id: ChannelId,
    val name: String,
    val isAvailable: Boolean = true,
    val format: String,
    val selectors: Selectors = Selectors()
) {
    @Serializable
    data class Selectors(
        val prefixes: List<Selector.Prefix> = listOf(),
        val regex: List<Selector.Regex> = listOf()
    )

    companion object {
        fun of(channel: ChatChannel, forPlayer: Player): ClientChatChannel {
            return ClientChatChannel(
                channel.id,
                channel.name,
                forPlayer.isChannelAvailable(channel),
                channel.format,
                channel.selectors.let {
                    Selectors(
                        it.filterIsInstance<Selector.Prefix>(),
                        it.filterIsInstance<Selector.Regex>()
                    )
                }
            )
        }
    }
}

val CLIENTBOUND_CHAT_SETTINGS_ENDPOINT = Endpoint<ChatSettingsPacket>()