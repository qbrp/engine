package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.chatChannelOf
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.showAllhearNotification
import org.lain.engine.client.render.showSpyNotification
import org.lain.engine.client.render.showTrackMessageTypingNotification
import org.lain.engine.client.render.ui.chat.ChatBarUi
import org.lain.engine.client.render.ui.chat.ChatHud
import org.lain.engine.client.render.ui.chat.ChatState
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings
import org.slf4j.LoggerFactory

class ClientChatManager(
    val gameSession: GameSession,
    settings: ClientChatSettings,
): ChatState {
    val client: EngineClient
        get() = gameSession.client

    var settings = settings
        private set
    var defaultChannel = settings.channels[settings.default] ?: error("Invalid default channel")
        private set
    private val logger = LoggerFactory.getLogger("Engine Chat Client")
    private var channels = mapOf<ChannelId, ClientChatChannel>()
    private var charTypeTimer = 0
    private var typingInChannel: ChannelId? = null
    var availableChannels = mapOf<ChannelId, ClientChatChannel>()
        private set

    override var chatBar: ChatBar? = null
        private set

    override var spy: Boolean = false
        set(value) {
            field = value
            client.showSpyNotification(value)
        }

    override var allhear: Boolean = false
        set(value) {
            field = value
            hud.invalidateMessages()
            client.showAllhearNotification(value)
        }

    var trackMessageTyping: Boolean = true
        set(value) {
            field = value
            if (!value) endTyping()
            client.showTrackMessageTypingNotification(value)
        }

    override val tick: Long
        get() = gameSession.ticks

    val hud by lazy { ChatHud(gameSession) }
    val barUi = ChatBarUi(this)
    val typingPlayers = TypingPlayers(gameSession)

    fun toggleSpy() {
        spy = !spy
    }

    fun toggleAllhear() {
        allhear = !allhear
    }

    fun toggleTypingIndicator() {
        trackMessageTyping = !trackMessageTyping
    }

    private val messagesMap: MutableMap<MessageId, ClientMessage> = mutableMapOf()
    val messages
        get() = messagesMap.values

    fun endTyping() {
        charTypeTimer = 0
        typingInChannel = null
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
        if (trackMessageTyping) {
            charTypeTimer = (charTypeTimer + 20).coerceAtMost(120)
            if (charTypeTimer > 20 && input.count() > 4) {
                if (typingInChannel != channel.id) {
                    typingInChannel = channel.id
                    client.handler.onChatStartTyping(channel.id)
                }
            }
        }
        return channel
    }

    fun tick() {
        charTypeTimer = (charTypeTimer - 1).coerceAtLeast(0)
        if (typingInChannel != null && charTypeTimer <= 0) {
            typingInChannel = null
            client.handler.onChatEndTyping()
        }
    }

    fun sendMessage(text: String) {
        val channels = availableChannels.values
        val (channel, content) = chatChannelOf(text, channels.toList(), defaultChannel)
        gameSession.handler.onChatMessageSend(content, channel.id)
        hud.selectedMessage = null
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

    fun addMessage(message: ClientMessage) {
        val chatBar = chatBar ?: return
        val channel = message.channel
        val author = message.source.author
        val isMentioned = message.isMentioned
        val isNotify = message.notify

        // Если есть точно такое же сообщение
        val similar = messages.toList()
            .takeLast(8)
            .find { it.channel != SYSTEM_CHANNEL && it.channel == channel && it.content == message.content }
        if (similar != null) {
            similar.repeat += 1
            return
        }

        messagesMap[message.id] = message

        if (isMentioned) {
            chatBar.markMentioned(channel.id)
        }

        val authorId = message.source.player?.id
        val mainPlayerId = gameSession.mainPlayer.id

        hud.addMessage(message)
        val visible = isMessageVisible(message, spy, chatBar)
        if (visible) {
            val showSelf = authorId != mainPlayerId || client.developerMode
            if (authorId != null && message.isSpeech && showSelf && client.options.chatBubbles) {
                val authorPlayer = gameSession.playerStorage.get(authorId)
                if (authorPlayer != null) {
                    gameSession.chatBubbleList.setChatBubble(authorPlayer, message.content.resolve(this))
                }
            }
            if (isMentioned || isNotify) {
                client.audioManager.playUiNotificationSound()
            }
            chatBar.markRead(channel.id)
        } else if ((!message.isSpy || spy) && authorId != mainPlayerId) {
            chatBar.markUnread(channel.id)
        }
    }

    fun deleteMessage(id: MessageId) {
        val toDelete = messagesMap[id] ?: return
        if (toDelete.source.player?.id == gameSession.mainPlayer.id) {
            deleteSelfMessage(toDelete)
        }
        messagesMap.remove(id)
        hud.removeMessage(id)
    }

    fun clearMessages() {
        messagesMap.clear()
        hud.clearMessages()
    }

    private fun deleteSelfMessage(message: ClientMessage) {
        client.handler.onChatMessageDelete(message)
    }

    fun updateSettings(new: ClientChatSettings) {
        val result = updateChatSettings(new, client.resources.chatBarConfiguration, chatBar)
        this.channels = result.channels
        this.availableChannels = result.availableChannels
        this.defaultChannel = result.defaultChannel
        this.chatBar = result.chatBar
        this.settings = new
        barUi.updateButtons(result.chatBar.sections)
        result.unavailableChannels.forEach { (channel, section) ->
            logger.warn("Канал $channel в секции панели чата ${section.name} не существует")
        }
    }

    fun updateOptions() {
        hud.invalidateMessages()
    }
}
