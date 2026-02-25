package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.EXCLAMATION
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings
import org.lain.engine.util.SPY_COLOR
import org.slf4j.LoggerFactory

class ClientEngineChatManager(
    private val eventBus: ChatEventBus,
    private val client: EngineClient,
    private val gameSession: GameSession,
    settings: ClientChatSettings,
) {
    var settings = settings
        private set
    var defaultChannel = settings.channels[settings.default] ?: error("Invalid default channel")
        private set
    private val logger = LoggerFactory.getLogger("Engine Chat Client")
    private var channels = mapOf<ChannelId, ClientChatChannel>()
    private var charTypeTimer = 0
    private var typing: ClientChatChannel? = null
    var availableChannels = mapOf<ChannelId, ClientChatChannel>()
        private set

    var chatBar: ChatBar? = null
        private set

    var spy: Boolean = false
        set(value) {
            field = value
            var status: String
            var description: String? = null
            if (value) {
                eventBus.onSpyEnable()
                status = "включена"
                description = "Доступны сообщения, не дошедшие до вас. Повторное нажатие отключит слежку."
            } else {
                eventBus.onSpyDisable()
                status = "выключена"
            }

            client.applyLittleNotification(
                LittleNotification(
                    "Слежка $status",
                    description,
                    color = SPY_COLOR,
                    sprite = EXCLAMATION
                )
            )
        }

    fun toggleSpy() {
        spy = !spy
    }

    val messages: MutableList<AcceptedMessage> = mutableListOf()

    fun disableChannel(id: ChannelId) {
        val channel = channels[id] ?: return
        eventBus.onChannelDisable(channel)
    }

    fun enableChannel(id: ChannelId) {
        val channel = channels[id] ?: return
        eventBus.onChannelEnable(channel)
    }

    fun endTyping() {
        charTypeTimer = 0
        typing = null
        client.handler.onChatEndTyping()
    }

    fun onTextInput(input: String): ClientChatChannel? {
        if (input.startsWith("/")) {
            return null
        }

        if (input.isEmpty()) {
            endTyping()
            return null
        }

        val channel = channelOf(input)
        charTypeTimer = (charTypeTimer + 20).coerceAtMost(120)
        if (charTypeTimer > 20 && input.count() > 4) {
            if (typing != channel) {
                typing = channel
                client.handler.onChatStartTyping(channel.id)
            }
        }
        return channel
    }

    fun tick() {
        charTypeTimer = (charTypeTimer - 1).coerceAtLeast(0)
        if (typing != null && charTypeTimer <= 0) {
            typing = null
            client.handler.onChatEndTyping()
        }
    }

    fun sendMessage(text: String) {
        val prefix = text.take(1)
        var content = text

        // Определяем канал
        val channels = availableChannels.values

        val channel: ClientChatChannel = channels.firstOrNull { ch ->
            val channelSelectors = ch.selectors
            val regexSelector = channelSelectors.regex.firstOrNull { Regex(it.expression).matches(text) }
            val prefixSelector = channelSelectors.prefixes.firstOrNull { prefix == it.value }

            val regexRemove = regexSelector?.remove
            if (regexSelector != null && regexRemove != null) {
                content = content.replace(regexRemove.toRegex(), "")
            } else if (prefixSelector != null) {
                content = text.drop(1)
            }

            prefixSelector != null || regexSelector != null
        } ?: defaultChannel

        gameSession.handler.onChatMessageSend(content, channel.id)
    }

    private fun channelOf(text: String): ClientChatChannel {
        val channels = availableChannels.values
        val prefix = text.take(1)
        return channels.firstOrNull { ch ->
            val channelSelectors = ch.selectors
            val regexSelector = channelSelectors.regex.firstOrNull { Regex(it.expression).matches(text) }
            val prefixSelector = channelSelectors.prefixes.firstOrNull { prefix == it.value }

            prefixSelector != null || regexSelector != null
        } ?: defaultChannel
    }

    fun addMessage(message: AcceptedMessage) {
        val chatBar = chatBar ?: return
        val channel = message.channel
        val author = message.source.author
        val isMentioned = message.isMentioned
        val isNotify = message.notify

        // Если есть точно такое же сообщение
        val similar = messages
            .takeLast(9)
            .find { it.channel == channel && it.display == message.display }
        if (similar != null) {
            similar.repeat += 1
            return
        }

        messages += message
        eventBus.onMessageAdd(message)

        if (isMentioned) {
            chatBar.markMentioned(channel.id)
        }

        if (isMessageVisible(message, spy, chatBar)) {
            val author = message.source.player
            val showSelf = author != gameSession.mainPlayer || client.developerMode
            if (author != null && message.isSpeech && showSelf && client.options.chatBubbles) {
                gameSession.chatBubbleList.setChatBubble(author, eventBus.getChatBubbleText(message.text))
            }
            if (isMentioned || isNotify) {
                client.audioManager.playUiNotificationSound()
            }
        } else if ((!message.isSpy || spy) && author.player != gameSession.mainPlayer) {
            chatBar.markUnread(channel.id)
        }
    }

    fun deleteMessage(id: MessageId) {
        val toDelete = messages.find { it.id == id } ?: return
        if (toDelete.source.player?.id == gameSession.mainPlayer.id) {
            deleteSelfMessage(toDelete)
        }
        eventBus.onMessageDelete(toDelete)
    }

    private fun deleteSelfMessage(message: AcceptedMessage) {
        client.handler.onChatMessageDelete(message)
    }

    fun updateSettings(new: ClientChatSettings) {
        val channelsMap = new.channels.toMutableMap()
        channelsMap[SYSTEM_CHANNEL.id] = SYSTEM_CHANNEL
        val channels = channelsMap.values

        this.channels = channelsMap
        this.availableChannels = channels.filter { it.isAvailable }.associateBy { it.id }
        this.defaultChannel = settings.channels[settings.default] ?: error("Invalid default channel")

        val configuration = client.resources.chatBarConfiguration
        val chatBarSections = configuration?.sections ?: channels.map { it.toChatBarSection() }
        val chatBarSections2 = chatBarSections.filter { section ->
            section.channels.any { channel ->
                if (!availableChannels.contains(channel)) {
                    logger.warn("Канал $channel в секции панели чата ${section.name} не существует")
                    false
                } else {
                    true
                }
            }
        }

        val cfg = ChatBarConfiguration(chatBarSections2)
        chatBar = chatBar?.copy(this, cfg) ?: ChatBar(cfg)
        settings = new

        eventBus.onSettingsUpdate(settings, chatBar!!)
    }
}