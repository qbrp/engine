package org.lain.engine.chat

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.lain.engine.player.EnginePlayer
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.util.Color
import org.lain.engine.util.Timestamp
import org.lain.engine.util.math.Pos
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.world.World
import org.lain.engine.world.pos
import org.lain.engine.world.world
import java.util.*

data class MessageAuthor(
    val name: String,
    val player: EnginePlayer? = null
)

data class MessageSource(
    val world: World,
    val author: MessageAuthor,
    val time: Timestamp,
    val position: Pos? = null,
) {
    val player
        get() = author.player
    val speech
        get() = player != null

    companion object {
        fun getSystem(world: World, time: Long = System.currentTimeMillis()): MessageSource {
            return MessageSource(world, MessageAuthor("Система"), Timestamp(time))
        }
        fun getPlayer(player: EnginePlayer, time: Long = System.currentTimeMillis()): MessageSource {
            return MessageSource(player.world, MessageAuthor(player.displayNameMiniMessage, player), Timestamp(time), player.pos)
        }
    }
}

@JvmInline
@Serializable(with = MessageIdSerializer::class)
value class MessageId(val value: UUID) {
    companion object {
        fun next() = MessageId(UUID.randomUUID())
    }
}

object MessageIdSerializer : KSerializer<MessageId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MessageId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MessageId) {
        encoder.encodeString(value.value.toString())
    }

    override fun deserialize(decoder: Decoder): MessageId {
        val uuid = UUID.fromString(decoder.decodeString())
        return MessageId(uuid)
    }
}

/**
 * Сообщение, поступающее в чат на обработку.
 * Содержит сырой неформатированный текст и обязательную ссылку на источник.
 */
data class IncomingMessage(
    val content: String,
    val volume: Float,
    val channel: ChannelId,
    val source: MessageSource
)

sealed class Acoustic {
    data class Distance(val radius: Int) : Acoustic()
    data class Realistic(
        val distort: Boolean
    ) : Acoustic()
    object Global : Acoustic()
}

sealed class Modifier {
    object Spectator : Modifier()
}

sealed class Selector {
    @Serializable
    data class Prefix(val value: String) : Selector()
    @Serializable
    data class Regex(
        val expression: String,
        val remove: String? = null
    ) : Selector()
}

@JvmInline
@Serializable
value class ChannelId(val value: String) {
    override fun toString(): String {
        return value
    }
}

data class ChatChannel(
    val id: ChannelId,
    val name: String,
    val format: String,
    val acoustic: Acoustic? = null,
    val modifiers: List<Modifier> = listOf(),
    val selectors: List<Selector> = listOf(),
    val speech: Boolean = false,
    val notify: Boolean = false,
    val permission: Boolean = false,
    val heads: Boolean = false,
    val mentions: Boolean = true,
    val background: Color? = null,
    val typeIndicatorRange: Int? = null,
    val typeIndicator: Boolean = true,
) {
    companion object {
        val DEFAULT = ChannelId("default")
        val SYSTEM = ChatChannel(
            ChannelId("system"),
            "Система",
            "{text}",
            Acoustic.Global
        )
    }
}

data class ChatChannelFindModifyResult(val channel: ClientChatChannel, val modifiedInput: String)

fun chatChannelOf(input: String, channels: List<ClientChatChannel>, defaultChannel: ClientChatChannel): ChatChannelFindModifyResult {
    val prefix = input.take(1)
    var content = input

    val channel: ClientChatChannel = channels.firstOrNull { ch ->
        val channelSelectors = ch.selectors
        val regexSelector = channelSelectors.regex.firstOrNull { Regex(it.expression).matches(content) }
        val prefixSelector = channelSelectors.prefixes.firstOrNull { prefix == it.value }

        val regexRemove = regexSelector?.remove
        if (regexSelector != null && regexRemove != null) {
            content = content.replace(regexRemove.toRegex(), "")
        } else if (prefixSelector != null) {
            content = content.drop(prefixSelector.value.count())
        }

        prefixSelector != null || regexSelector != null
    } ?: defaultChannel
    return ChatChannelFindModifyResult(channel, content)
}

data class OutcomingMessage(
    val text: String,
    val source: MessageSource,
    val channel: ChannelId,
    val mentioned: Boolean = false,
    val notify: Boolean = false,
    val speech: Boolean = false,
    val volumes: EngineChat.Volumes? = null,
    val placeholders: Map<String, String> = mapOf(),
    val isSpy: Boolean = false,
    val head: Boolean = false,
    val color: Color? = null,
    val id: MessageId
)