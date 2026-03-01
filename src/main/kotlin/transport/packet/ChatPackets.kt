package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.chat.*
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.Color
import org.lain.engine.util.math.ImmutableVec3
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
    val volume: EngineChat.Volumes?,
    val isSpy: Boolean,
    val placeholders: Map<String, String>,
    val heads: Boolean,
    val notify: Boolean,
    val color: Color? = null,
    val id: MessageId
) : Packet

val CLIENTBOUND_CHAT_MESSAGE_ENDPOINT = Endpoint<OutcomingChatMessagePacket>()

@Serializable
data class IncomingChatMessagePacket(
    val text: String,
    val channel: ChannelId
) : Packet

val SERVERBOUND_CHAT_MESSAGE_ENDPOINT = Endpoint<IncomingChatMessagePacket>()

@Serializable
data class DeleteChatMessagePacket(val message: MessageId) : Packet

val SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT = Endpoint<DeleteChatMessagePacket>()

val CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT = Endpoint<DeleteChatMessagePacket>()

@Serializable
data class ClientChatSettings(
    val channels: Map<ChannelId, ClientChatChannel>,
    val default: ChannelId,
    val placeholders: Map<String, String>,
) {
    companion object {
        fun of(settings: EngineChatSettings, player: EnginePlayer): ClientChatSettings {
            return ClientChatSettings(
                channelsOf(settings, player),
                settings.defaultChannel.id,
                settings.placeholders,
            )
        }
        fun channelsOf(settings: EngineChatSettings, player: EnginePlayer) = (settings.channels + settings.pmChannel)
            .associate { it.id to ClientChatChannel.of(it, player) }
    }
}

@Serializable
data class ClientChatChannel(
    val id: ChannelId,
    val name: String,
    val isAvailable: Boolean = true,
    val format: String,
    val selectors: Selectors = Selectors(),
    val spy: Boolean = true
) {
    @Serializable
    data class Selectors(
        val prefixes: List<Selector.Prefix> = listOf(),
        val regex: List<Selector.Regex> = listOf()
    )

    companion object {
        fun of(channel: ChatChannel, forPlayer: EnginePlayer): ClientChatChannel {
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

@Serializable
data class ChatTypingStartPacket(val channel: ChannelId) : Packet

val SERVERBOUND_CHAT_TYPING_START_ENDPOINT = Endpoint<ChatTypingStartPacket>()

@Serializable
object ChatTypingEndPacket : Packet


val SERVERBOUND_CHAT_TYPING_END_ENDPOINT = Endpoint<ChatTypingEndPacket>()

@Serializable
data class ChatTypingPlayerPacket(val player: PlayerId) : Packet

val CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT = Endpoint<ChatTypingPlayerPacket>("chat-typing-player-start")

val CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT = Endpoint<ChatTypingPlayerPacket>("chat-typing-player-end")