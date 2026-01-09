package org.lain.engine.client.mc

import net.minecraft.client.gui.hud.ChatHudLine
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.util.ChatMessages
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.client.chat.ChatBar
import org.lain.engine.client.chat.ChatEventBus
import org.lain.engine.client.chat.EngineChatMessage
import org.lain.engine.client.chat.SYSTEM_CHANNEL
import org.lain.engine.client.mc.render.ChatChannelsBar
import org.lain.engine.client.mixin.ui.ChatHudAccessor
import org.lain.engine.util.HIGH_VOLUME_COLOR
import org.lain.engine.util.LOW_VOLUME_COLOR
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings
import org.lain.engine.util.Color
import org.lain.engine.util.lerp
import org.lain.engine.util.text.parseMiniMessage
import kotlin.collections.set
import kotlin.math.pow
import kotlin.random.Random

object MinecraftChat : ChatEventBus {
    private val client by injectClient()
    private val messageByContentCache = mutableMapOf<String, EngineChatMessage>()
    private val messageCache = mutableMapOf<EngineChatMessage, MessageData>()
    private val chatHudLines = mutableMapOf<ChatHudLine.Visible, ChatHudLineData>()

    private val spyMessages = mutableMapOf<ChatHudLine.Visible, StoredMessage>()

    private data class StoredMessage(
        val line: ChatHudLine.Visible,
        var isHidden: Boolean,
    )

    private var allMessages = mutableListOf<StoredMessage>()
    private val messageByLine = mutableMapOf<ChatHudLine.Visible, StoredMessage>()
    private val hiddenMessages = mutableMapOf<ChatHudLine.Visible, StoredMessage>()
    private val hiddenChannelMessages = mutableMapOf<ChannelId, MutableList<ChatHudLine.Visible>>()
    private val spy get() = chatManager?.spy ?: false

    private val chatManager get() = client.gameSession?.chatManager
    private val chatHud get() =  MinecraftClient.inGameHud.chatHud
    var selectedMessage: MessageData? = null
    val channelsBar = ChatChannelsBar()

    private var textShakingBoost = 0f
    val chatInputTextShaking: Float get() {
        val volume = client.gameSession?.vocalRegulator?.volume ?: return  0f
        val currentVolume = volume.value
        val baseVolume = volume.base
        val maxVolume = volume.max
        return if (isWritingCommand) {
            0f
        } else {
            val minVolume = baseVolume + (maxVolume - baseVolume) * client.options.chatInputShakingThreshold
            val shakePower = currentVolume - minVolume

            shakePower.coerceAtLeast(0f) / minVolume + textShakingBoost
        }
    }
    var isWritingCommand = false

    data class MessageData(
        val node: ChatHudLine,
        val author: PlayerListEntry?,
        val brokenChatBubbleLines: List<OrderedText>,
        val engineMessage: EngineChatMessage
    ) {
        val debugText = Text.of(" [volume ${engineMessage.volume}]")
    }

    data class ChatHudLineData(val line: ChatHudLine.Visible, val isFirst: Boolean, val isLast: Boolean, val message: MessageData) {
        val channelId get() = message.engineMessage.channel.id
        val messageId get() = message.engineMessage.id.value
    }

    fun messageIdOf(message: EngineChatMessage) = message.id.value

    fun updateSelectedMessage(chatHudLine: ChatHudLine.Visible?) {
        val toSet = chatHudLine?.let { getChatHudLineData(it) }?.message
        selectedMessage = if (selectedMessage == toSet) {
            null
        } else {
            toSet
        }
    }

    fun isEngineMessage(chatHudLine: ChatHudLine) = messageByContentCache.contains(chatHudLine.content.string)

    fun getMessageData(engineChatMessage: EngineChatMessage): MessageData? {
        return messageCache[engineChatMessage]
    }

    fun getChatHudLineData(visible: ChatHudLine.Visible): ChatHudLineData? {
        return chatHudLines[visible]
    }

    /**
     * @return Отменить ли добавление сообщения в чат
     */
    fun storeChatHudLine(
        node: ChatHudLine,
        chatHudLine: ChatHudLine.Visible,
        isFirst: Boolean,
        isLast: Boolean,
        index: Int
    ): Boolean {
        val content = node.content.string
        val engineMessage = getEngineMessage(content) ?: run {
            EngineChatMessage(
                content,
                content,
                SYSTEM_CHANNEL,
                MessageSource.getSystem(client.gameSession?.world ?: return false),
                id = MessageId.next()
            ).also {
                messageByContentCache[content] = it
                chatManager?.addMessage(it)
                return true
            }
        }

        val storedMessage = StoredMessage(chatHudLine, false)
        allMessages.add(0, storedMessage)
        messageByLine[chatHudLine] = storedMessage

        val chatManager = chatManager ?: return false
        val chatBar = chatManager.chatBar ?: return false
        val channel = engineMessage.channel
        val author = engineMessage.source.player
        val authorEntity = author?.let { MinecraftClient.networkHandler?.getPlayerListEntry(it.id.value) }
        val messageData = MessageData(
            node,
            authorEntity,
            ChatMessages.breakRenderedChatMessageLines(
                engineMessage.text.parseMiniMessage(),
                client.options.chatBubbleLineWidth,
                MinecraftClient.textRenderer
            ),
            engineMessage
        )
        val data = ChatHudLineData(chatHudLine, isFirst, isLast, messageData)

        val isSpy = engineMessage.isSpy
        val visible = channel.isAvailable && !chatBar.isHidden(channel.id) && (isSpy && spy || !isSpy)

        if (engineMessage.isSpy) {
            spyMessages[chatHudLine] = storedMessage
        }

        chatHudLines[chatHudLine] = data
        messageCache[engineMessage] = messageData
        return if (visible) {
            false
        } else {
            hiddenMessages[chatHudLine] = storedMessage
            hiddenChannelMessages.getOrPut(channel.id) { mutableListOf() }.add(chatHudLine)
            true
        }
    }

    fun deleteChatHudLine(line: ChatHudLine.Visible) {
        val data = chatHudLines[line] ?: return
        messageCache.remove(data.message.engineMessage)
        hiddenMessages.remove(line)
        hiddenChannelMessages.remove(data.channelId)
        messageByContentCache.remove(data.message.node.content.string)
        spyMessages.remove(line)
        messageByLine.remove(line)?.let { allMessages.remove(it) }
    }

    fun deleteMessage(chatMessage: EngineChatMessage) {
        val messages = chatHudLines.values.filter { it.message.engineMessage.id == chatMessage.id }
        val accessor = chatHud as ChatHudAccessor
        messages.forEach { message ->
            val line = message.line
            accessor.`engine$getVisibleMessages`().remove(line)
            accessor.`engine$getMessages`().remove(message.message.node)
            deleteChatHudLine(line)
        }
    }

    fun storeMessageContent(content: String, message: EngineChatMessage) {
        messageByContentCache[content] = message
    }

    fun getEngineMessage(content: String) = messageByContentCache[content]

    fun shouldRenderDebugInfo() = client.developerMode

    fun clearChatData() {
        chatHudLines.clear()
        messageByContentCache.clear()
        messageCache.clear()
        allMessages.clear()
        hiddenChannelMessages.clear()
        hiddenMessages.clear()
        spyMessages.clear()
        selectedMessage = null
    }

    override fun onMessageAdd(message: EngineChatMessage) {
        val displayText = message.display.parseMiniMessage()
        storeMessageContent(displayText.string, message)
        chatHud.addMessage(displayText)
    }

    override fun onMessageDelete(message: EngineChatMessage) {
        deleteMessage(message)
    }

    override fun onChannelEnable(channel: ClientChatChannel) {
        val hiddenMessages = hiddenChannelMessages[channel.id]
            ?.filter {
                val isSpy = getChatHudLineData(it)?.message?.engineMessage?.isSpy ?: false
                (isSpy && spy) || !isSpy
            }
        ?: return
        restoreHiddenMessages(hiddenMessages)
        hiddenMessages.forEach { message ->
            hiddenChannelMessages.remove(channel.id)
        }
    }

    override fun onChannelDisable(channel: ClientChatChannel) {
        hideVisibleMessagesIf { line, data ->
            val messageChannel = data.channelId
            channel.id == messageChannel
        }.forEach {
            hiddenChannelMessages.computeIfAbsent(it.channelId) { mutableListOf() }.add(it.line)
        }
    }

    override fun onSpyEnable() {
        restoreHiddenMessages(spyMessages.keys.toList())
    }

    override fun onSpyDisable() {
        hideVisibleMessagesIf { line, data -> spyMessages.contains(line) }
    }

    private fun hideVisibleMessagesIf(predicate: (line: ChatHudLine.Visible, data: ChatHudLineData) -> Boolean): List<ChatHudLineData> {
        val toHideChatHudLine = mutableListOf<ChatHudLineData>()
        val toHide = allMessages.filter { message ->
            val line = message.line
            val data = getChatHudLineData(line) ?: return@filter false
            predicate(line, data)
                .also { if (it) toHideChatHudLine += data }
        }
        if (toHide.isNotEmpty()) {
            toHide.forEach {
                it.isHidden = true
                hiddenMessages[it.line] = it
            }
            restore()
        }
        return toHideChatHudLine
    }

    private fun restoreHiddenMessages(messages: List<ChatHudLine.Visible>) {
        val toRestore = messages.mapNotNull { hiddenMessages[it] }
        if (toRestore.isNotEmpty()) {
            toRestore.forEach {
                it.isHidden = false
                hiddenMessages.remove(it.line)
            }
            restore()
        }
    }

    private fun restore() {
        val visibleMessages = (chatHud as ChatHudAccessor).`engine$getVisibleMessages`()
        visibleMessages.clear()
        visibleMessages.addAll(
            allMessages
                .filter { !it.isHidden }
                .map { it.line }
        )
    }

    override fun onSettingsUpdate(settings: ClientChatSettings, chatBar: ChatBar) {
        channelsBar.updateButtons(chatBar.configuration.sections)
    }

    fun updateChatInput(input: String) {
        isWritingCommand = input.startsWith("/")
    }

    fun updateChatShaking(dt: Float) {
        textShakingBoost = lerp(textShakingBoost, 0f, 1 - 0.5f.pow(dt))
    }

    fun getRandomShakeTranslation(): Float {
        return Random.nextFloat() * chatInputTextShaking * client.options.chatInputShakingForce
    }

    fun getChatFieldColor(color: Int): Int {
        val gameSession = client.gameSession ?: return color
        val volume = gameSession.vocalRegulator.volume
        val currentVolume = volume.value
        val baseVolume = volume.base
        val maxVolume = volume.max
        return if (currentVolume > baseVolume) {
            val a = currentVolume / maxVolume
            val a1 = a.coerceAtLeast(0f)
            HIGH_VOLUME_COLOR.withAlpha((a1 * 0.4f * 255).toInt())
        } else {
            val a = (baseVolume - currentVolume) / baseVolume
            val a1 = a.coerceAtLeast(0f)
            LOW_VOLUME_COLOR.withAlpha((a1 * 0.3f * 255).toInt())
        }.integer
    }

    override fun onMessageVolumeUpdate(old: Float, new: Float) {
        val delta = new - old
        if (delta > 0) {
            textShakingBoost = (delta * chatInputTextShaking * 70).coerceAtMost(2f)
        }
    }

    override fun getChatBubbleText(content: String): String {
        return content.parseMiniMessage().string
    }
}