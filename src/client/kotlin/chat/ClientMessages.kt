package org.lain.engine.client.chat

import org.lain.engine.chat.*
import org.lain.engine.client.GameSession
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings
import org.lain.engine.util.Color
import org.lain.engine.util.Timestamp
import org.lain.engine.world.World

data class AcceptedMessage(
    val text: String,
    val displayText: String,
    val undistortedDisplayText: String,
    val channel: ClientChatChannel,
    val source: MessageSource,
    val isMentioned: Boolean = false,
    val isSpeech: Boolean = false,
    val volume: EngineChat.Volumes? = null,
    val isSpy: Boolean = false,
    val showHead: Boolean = false,
    val notify: Boolean = false,
    val background: Color? = null,
    val id: MessageId,
    var repeat: Int = 1,
    val isVanilla: Boolean = false,
) {
    val backgroundColorInt = background?.integer
}

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

private fun formatRegex(text: String, regex: ChatRegexSettings, playerNames: List<String>): String {
    var text = text
    regex.replace.forEach { rule ->
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
    return text
}

private fun formatPlaceholders(text: String, placeholders: Map<String, String>): String {
    var text = text
    placeholders.forEach { (old, new) ->
        text = text
            .replace("{$old}", new)
    }

    text = Regex("\\{([^|{}]+)\\|([^{}]*)\\}").replace(text) { match ->
        val name = match.groupValues[1]
        val defaultValue = match.groupValues.getOrNull(2)
        val placeholder = placeholders[name]

        placeholder ?: (defaultValue ?: "")
    }
    return text
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
    val text = formatRegex(message.text, format.regex, playerNames)

    val placeholders = (placeholders + message.placeholders).toMutableMap()
    placeholders["text"] = text
    var display = formatPlaceholders(channel.format, placeholders)

    if (message.isSpy) {
        display = format.spy.replace("{original}", display)
    }

    val undistortedTextPlaceholders = placeholders.toMutableMap()
    undistortedTextPlaceholders["text"] = formatRegex(message.undistortedText, format.regex, playerNames)
    val undistortedDisplay = formatPlaceholders(channel.format, undistortedTextPlaceholders)

    return AcceptedMessage(
        text,
        display,
        undistortedDisplay,
        channel,
        message.source,
        message.mentioned,
        message.speech,
        message.volumes,
        message.isSpy,
        message.head,
        message.notify,
        message.color,
        message.id,
    )
}

fun MessageSource.Companion.getSystemClient(world: World) = MessageSource(
    MessageSource.World(world.id, mapOf()),
    MessageAuthor(SYSTEM_CHANNEL.name),
    Timestamp()
)

fun LiteralSystemEngineChatMessage(gameSession: GameSession, content: String) = AcceptedMessage(
    content,
    content,
    content,
    SYSTEM_CHANNEL,
    MessageSource.getSystemClient(gameSession.world),
    id = MessageId.next()
)


fun LiteralSystemEngineChatMessage(world: World, content: String, isSpy: Boolean = false) = AcceptedMessage(
    content,
    content,
    content,
    SYSTEM_CHANNEL,
    MessageSource.getSystemClient(world),
    id = MessageId.next(),
    isSpy = isSpy
)

data class ChatSettingsUpdateResult(
    val channels: Map<ChannelId, ClientChatChannel>,
    val availableChannels: Map<ChannelId, ClientChatChannel>,
    val defaultChannel: ClientChatChannel,
    val chatBar: ChatBar,
    val unavailableChannels: List<Unavailable>,
) {
    data class Unavailable(val channel: ChannelId, val section: ChatBarSection)
}

fun updateChatSettings(
    settings: ClientChatSettings,
    chatBarConfiguration: ChatBarConfiguration?,
    chatBar: ChatBar?,
): ChatSettingsUpdateResult {
    val channelsMap = settings.channels.toMutableMap()
    channelsMap[SYSTEM_CHANNEL.id] = SYSTEM_CHANNEL
    val unavailable = mutableSetOf<ChatSettingsUpdateResult.Unavailable>()
    val channels = channelsMap.values
    val availableChannels = channels.filter { it.isAvailable }.associateBy { it.id }
    val defaultChannel = settings.channels[settings.default] ?: error("Invalid default channel")

    val unvalidatedChatBarSections = chatBarConfiguration?.sections ?: channelsMap.values.map { it.toChatBarSection() }
    val validatedChatBarSections = unvalidatedChatBarSections.filter { section ->
        section.channels.any { channel ->
            if (!availableChannels.contains(channel)) {
                unavailable += ChatSettingsUpdateResult.Unavailable(channel, section)
                false
            } else {
                true
            }
        }
    }

    val validatedChatBarConfiguration = ChatBarConfiguration(validatedChatBarSections)
    val newChatBar = ChatBar(validatedChatBarConfiguration, chatBar)
    return ChatSettingsUpdateResult(
        channelsMap,
        availableChannels,
        defaultChannel,
        newChatBar,
        unavailable.toList()
    )
}