package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.chatChannelOf
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
        val channels = availableChannels.values
        val (channel, content) = chatChannelOf(text, channels.toList(), defaultChannel)
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

        val authorId = message.source.player?.id
        val mainPlayerId = gameSession.mainPlayer.id
        if (isMessageVisible(message, spy, chatBar)) {
            val showSelf = authorId != mainPlayerId || client.developerMode
            if (authorId != null && message.isSpeech && showSelf && client.options.chatBubbles) {
                val authorPlayer = gameSession.playerStorage.get(authorId)
                if (authorPlayer != null) {
                    gameSession.chatBubbleList.setChatBubble(authorPlayer, eventBus.getChatBubbleText(message.text))
                }
            }
            if (isMentioned || isNotify) {
                client.audioManager.playUiNotificationSound()
            }
        } else if ((!message.isSpy || spy) && authorId != mainPlayerId) {
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
        val result = updateChatSettings(new, client.resources.chatBarConfiguration, chatBar)
        this.channels = result.channels
        this.availableChannels = result.availableChannels
        this.defaultChannel = result.defaultChannel
        this.chatBar = result.chatBar
        this.settings = new
        eventBus.onSettingsUpdate(new, result.chatBar)
        result.unavailableChannels.forEach { (channel, section) ->
            logger.warn("Канал $channel в секции панели чата ${section.name} не существует")
        }
    }
}