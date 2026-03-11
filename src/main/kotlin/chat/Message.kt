package org.lain.engine.chat

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.lain.engine.player.*
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.util.Color
import org.lain.engine.util.Timestamp
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.util.math.snapshot
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.pos
import org.lain.engine.world.world
import java.util.*

fun EnginePlayer.messageSource(channel: ChatChannel) = MessageSource.Player(
    id,
    pos.snapshot(),
    isSpectating,
    chatHeadsEnabled,
    eyePos.snapshot(),
    displayName,
    displayNameMiniMessage,
    username,
    isChannelAvailableToRead(channel),
    isChannelAvailableToWrite(channel),
    isChatOperator
)

fun World.messageSource(channel: ChatChannel) = MessageSource.World(id, players.associateBy { it.id }.mapValues { (_, player) -> player.messageSource(channel) })

@Serializable
data class MessageAuthor(
    val name: String,
    val player: MessageSource.Player? = null,
)

@Serializable
data class MessageSource(
    val world: World,
    val author: MessageAuthor,
    val time: Timestamp,
    val position: ImmutableVec3? = null,
) {
    val player
        get() = author.player
    val speech
        get() = player != null

    @Serializable
    data class World(val id: WorldId, val players: Map<PlayerId, Player>)

    @Serializable
    data class Player(
        val id: PlayerId,
        val pos: ImmutableVec3,
        val isSpectating: Boolean,
        val chatHeadsEnabled: Boolean,
        val eyePos: ImmutableVec3,
        val displayName: String,
        val displayNameMiniMessage: String,
        val userName: String,
        val readPermission: Boolean,
        val writePermission: Boolean,
        val chatOperator: Boolean
    )

    companion object {
        fun getSystem(
            world: org.lain.engine.world.World,
            time: Long = System.currentTimeMillis()
        ): MessageSource {
            return getWorld(world, "Система", ChatChannel.SYSTEM, time=time)
        }

        fun getWorld(
            world: org.lain.engine.world.World,
            author: String,
            channel: ChatChannel,
            pos: ImmutableVec3? = null,
            time: Long = System.currentTimeMillis()
        ): MessageSource {
            return MessageSource(world.messageSource(channel), MessageAuthor(author), Timestamp(time), pos)
        }

        fun getPlayer(
            player: EnginePlayer,
            channel: ChatChannel,
            time: Long = System.currentTimeMillis()
        ): MessageSource {
            return MessageSource(
                player.world.messageSource(channel),
                MessageAuthor(player.displayNameMiniMessage, player.messageSource(channel)),
                Timestamp(time),
                ImmutableVec3(player.pos)
            )
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

@Serializable
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