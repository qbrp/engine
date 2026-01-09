package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.client.render.EXCLAMATION
import org.lain.engine.util.SPY_COLOR
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings
import org.slf4j.LoggerFactory

class ClientEngineChatManager(
    private val eventBus: ChatEventBus,
    private val client: EngineClient,
    private val gameSession: GameSession,
    settings: ClientChatSettings,
) {
    private val logger = LoggerFactory.getLogger("Engine Chat Client")
    var settings = settings
        private set
    var defaultChannel = settings.channels[settings.default] ?: error("Invalid default channel")
        private set
    private var channels = mapOf<ChannelId, ClientChatChannel>()
    private var availableChannels = mapOf<ChannelId, ClientChatChannel>()
    private val systemChannel = SYSTEM_CHANNEL

    var chatBar: ChatBar? = null
        private set

    var spy: Boolean = false
        set(value) {
            var notification: String
            var description: String? = null
            if (value) {
                eventBus.onSpyEnable()
                notification = "включена"
                description = "Доступны сообщения, не дошедшие до вас. Повторное нажатие отключит слежку."
            } else {
                eventBus.onSpyDisable()
                notification = "выключена"
            }

            field = value

            client.applyLittleNotification(
                LittleNotification(
                    "Слежка $notification",
                    description,
                    color = SPY_COLOR,
                    sprite = EXCLAMATION
                )
            )
        }

    fun toggleSpy() {
        spy = !spy
    }

    private val messages: MutableList<EngineChatMessage> = mutableListOf()

    fun getChannel(id: ChannelId) = channels[id] ?: systemChannel

    fun disableChannel(id: ChannelId) {
        val channel = channels[id] ?: return
        eventBus.onChannelDisable(channel)
    }

    fun enableChannel(id: ChannelId) {
        val channel = channels[id] ?: return
        eventBus.onChannelEnable(channel)
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

    fun addMessage(message: EngineChatMessage) {
        messages += message
        eventBus.onMessageAdd(message)
        val chatBar = chatBar ?: return
        val channel = message.channel
        val isSpy = message.isSpy
        val isMentioned = message.isMentioned
        val visibleAsSpy = (isSpy && spy || !isSpy) && channel.spy
        val visible = channel.isAvailable && !chatBar.isHidden(channel.id) && visibleAsSpy
        val markRead = visible || !visibleAsSpy

        if (!markRead) {
            chatBar.markUnread(channel.id)
        }

        if (isMentioned) {
            chatBar.markMentioned(channel.id)
        }

        if (visible) {
            val author = message.source.player
            if (author != null && message.isSpeech && (author != gameSession.mainPlayer || client.developerMode)) {
                gameSession.chatBubbleList.setChatBubble(author, eventBus.getChatBubbleText(message.text))
            }
            if (isMentioned) {
                client.audioManager.playUiNotificationSound()
            }
        }
    }

    fun deleteMessage(id: MessageId) {
        val toDelete = messages.find { it.id == id } ?: return
        if (toDelete.source.player?.id == gameSession.mainPlayer.id) {
            deleteSelfMessage(toDelete)
        }
        eventBus.onMessageDelete(toDelete)
    }

    private fun deleteSelfMessage(message: EngineChatMessage) {
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
            section.channels.none { channel ->
                if (!availableChannels.contains(channel)) {
                    logger.warn("Канал $channel в секции панели чата ${section.name} не существует")
                    true
                } else {
                    false
                }
            }
        }

        chatBar = ChatBar(
            ChatBarConfiguration(chatBarSections2),
            this
        )

        settings = new

        eventBus.onSettingsUpdate(settings, chatBar!!)
    }
}