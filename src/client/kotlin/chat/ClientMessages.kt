package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.GameSession
import org.lain.engine.player.username
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.world.World

data class AcceptedMessage(
    val text: String,
    val display: String,
    val channel: ClientChatChannel,
    val source: MessageSource,
    val isMentioned: Boolean = false,
    val isSpeech: Boolean = false,
    val volume: Float? = null,
    val isSpy: Boolean = false,
    val showHead: Boolean = false,
    val id: MessageId,
    var repeat: Int = 1,
    val isVanilla: Boolean = false
)

val SYSTEM_CHANNEL = ClientChatChannel(
    ChannelId("system"),
    "Система",
    format = "{text}",
    spy = false
)

// Сначала решаем, можем ли мы вообще увидеть сообщение. Только в случае, если канал доступен и мы его отследили (или оно обычное)
fun isMessageAvailable(
    message: AcceptedMessage,
    spy: Boolean
): Boolean {
    return message.channel.isAvailable && !message.isSpy || spy
}

// Решаем, видно ли в данный момент сообщение пользователю. Руководствуемся данными из чат-бара: сообщение должно быть доступно, а канал не скрыт
fun isMessageVisible(
    message: AcceptedMessage,
    chatBar: ChatBar,
    available: Boolean
): Boolean {
    return available && !chatBar.isHidden(message.channel.id)
}

fun isMessageVisible(message: AcceptedMessage, spy: Boolean, chatBar: ChatBar): Boolean {
    return isMessageVisible(message, chatBar, isMessageAvailable(message, spy))
}

fun acceptOutcomingMessage(
    message: OutcomingMessage,
    channels: Map<ChannelId, ClientChatChannel>,
    defaultChannel: ClientChatChannel,
    placeholders: Map<String, String>,
    format: ChatFormatSettings,
    playerNames: List<String>
): AcceptedMessage {
    val channelId = message.channel
    val channel = channels[channelId] ?: defaultChannel
    var text = message.text

    format.regex.replace.forEach { rule ->
        val regex = Regex(rule.exp)

        text = regex.replace(text) { match ->
            val value = match.value
            val cleaned = rule.remove?.let {
                value.replace(it, "")
            } ?: value

            rule.value.replace("{match}", cleaned)
        }
    }

    text = Regex("@[A-Za-z0-9_]+").replace(text) { match ->
        val nickname = match.value.drop(1)
        if (nickname in playerNames) "<bold><yellow>@${nickname}</yellow></bold>" else match.value
    }

    var display = channel.format
    val placeholders = (placeholders + message.placeholders).toMutableMap()
    placeholders["text"] = text
    placeholders.forEach { old, new ->
        display = display
            .replace("{$old}", new)
    }

    display = Regex("\\{([^|{}]+)\\|([^{}]*)\\}").replace(display) { match ->
        val name = match.groupValues[1]
        val defaultValue = match.groupValues.getOrNull(2)
        val placeholder = placeholders[name]

        placeholder ?: (defaultValue ?: "")
    }

    if (message.isSpy) {
        display = format.spy.replace("{original}", display)
    }

    return AcceptedMessage(
        text,
        display,
        channel,
        message.source,
        message.mentioned,
        message.speech,
        message.volume,
        message.isSpy,
        message.head,
        message.id
    )
}

fun LiteralSystemEngineChatMessage(gameSession: GameSession, content: String) = AcceptedMessage(
    content,
    content,
    SYSTEM_CHANNEL,
    MessageSource.getSystem(gameSession.world),
    id = MessageId.next()
)


fun LiteralSystemEngineChatMessage(world: World, content: String, isSpy: Boolean = false) = AcceptedMessage(
    content,
    content,
    SYSTEM_CHANNEL,
    MessageSource.getSystem(world),
    id = MessageId.next(),
    isSpy = isSpy
)