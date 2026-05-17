package org.lain.engine.client.mc

import net.minecraft.client.GuiMessage
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ComponentRenderUtils
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.player.PlayerSkin
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.client.chat.*
import org.lain.engine.client.mixin.chat.ChatHudAccessor
import org.lain.engine.client.render.ui.ChatChannelsBar
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.mc.Text
import org.lain.engine.mc.displayNameMiniMessage
import org.lain.engine.mc.literalText
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings
import org.lain.engine.util.HIGH_VOLUME_COLOR
import org.lain.engine.util.LOW_VOLUME_COLOR
import org.lain.engine.util.math.lerp
import java.util.*
import kotlin.math.pow
import kotlin.random.Random

object MinecraftChat : ChatEventBus {
    var guiGraphics: GuiGraphics? = null
    private val client by injectClient()
    private val chatLines = IdentityHashMap<GuiMessage.Line, ChatLineData>()
    private val chatMessages = IdentityHashMap<GuiMessage, ChatMessageData>()
    private val contentToEngineMessages = mutableMapOf<String, AcceptedMessage>()

    private data class MessageIdentity(val tick: Int, val content: String)
    private val identityContentToEngineMessages = mutableMapOf<MessageIdentity, AcceptedMessage>()

    private var allMessages = mutableListOf<ChatLineData>()
    private val spy get() = chatManager?.spy ?: false

    private val chatHud get() =  MinecraftClient.gui.chat
    val chatManager get() = client.gameSession?.chatManager
    var selectedMessage: ChatMessageData? = null
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
    val typingPlayers = mutableSetOf<TypingPlayer>()

    data class TypingPlayer(val id: PlayerId, val skinTextures: PlayerSkin, val name: Text) {
        override fun equals(other: Any?): Boolean {
            return other is TypingPlayer && id == other.id
        }
    }

    fun registerEndpoints() {
        CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT.registerClientReceiver {
            val gameSession = client.gameSession ?: return@registerClientReceiver
            val playerListEntry = getPlayerInfo(player) ?: return@registerClientReceiver
            val enginePlayer = gameSession.playerStorage.get(player) ?: return@registerClientReceiver

            if (enginePlayer != gameSession.mainPlayer || client.developerMode) {
                typingPlayers.add(
                    TypingPlayer(
                        player,
                        playerListEntry.skin,
                        enginePlayer.displayNameMiniMessage.parseMiniMessageClient()
                    )
                )
            }
        }

        CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT.registerClientReceiver {
            if (client.gameSession?.playerStorage?.get(player) == null) {
                typingPlayers.clear() // Если что-то сломалось
                return@registerClientReceiver
            }
            typingPlayers.removeIf { player == it.id }
        }
    }

    private fun getPlayerInfo(id: PlayerId): PlayerInfo? {
        return MinecraftClient.connection?.onlinePlayers?.find { it.profile.id == id.value }
    }

    data class ChatMessageData(
        val node: GuiMessage,
        val author: PlayerInfo?,
        val brokenChatBubbleLines: List<FormattedCharSequence>,
        val engineMessage: AcceptedMessage
    ) {
        val debugText: Text? by lazy {
            val volume = engineMessage.volume
            volume?.let { (input, result) -> literalText(" [input $input, result $result]") }
        }
    }

    data class ChatLineData(val line: GuiMessage.Line, val isFirst: Boolean, val isLast: Boolean, val message: ChatMessageData) {
        val channelId get() = message.engineMessage.channel.id
        val messageId get() = message.engineMessage.id.value
    }

    fun onCloseChatInput() {
        chatManager?.endTyping()
    }

    fun updateSelectedMessage(chatHudLine: GuiMessage.Line?) {
        val toSet = chatHudLine?.let { getGuiMessageData(it) }?.message
        selectedMessage = if (selectedMessage == toSet) {
            null
        } else {
            toSet
        }
    }

    fun isMessageStored(chatHudLine: GuiMessage) = chatMessages.contains(chatHudLine)

    fun isEngineMessage(chatHudLine: GuiMessage) = identityContentToEngineMessages[MessageIdentity(chatHudLine.addedTime, chatHudLine.content.string)] != null

    fun getGuiMessageData(visible: GuiMessage.Line): ChatLineData? {
        return chatLines[visible]
    }

    /**
     * @return Отменить ли добавление сообщения в чат
     */
    fun storeGuiMessage(
        node: GuiMessage,
        chatHudLine: GuiMessage.Line,
        isFirst: Boolean,
        isLast: Boolean,
        index: Int
    ): Boolean {
        var isRepeat = false
        val content = node.content.string
        val chatManager = chatManager ?: return false
        val chatBar = requireChatBar()

        val engineMessage = if (isEngineMessage(node)) {
            contentToEngineMessages[content]
        } else {
            AcceptedMessage(
                content,
                content,
                SYSTEM_CHANNEL,
                MessageSource.getSystemClient(client.gameSession?.world ?: return true),
                id = MessageId.next(),
                isVanilla = true
            ).also {
                contentToEngineMessages[content] = it

                val similar = chatManager.messages
                    .takeLast(9)
                    .find { msg -> msg.channel == it.channel && msg.display == it.display }

                chatManager.addMessage(it)

                isRepeat = similar != null
            }
        } ?: return false

        val author = engineMessage.source.player
        val authorEntity = author?.let { MinecraftClient.connection?.getPlayerInfo(it.id.value) }
        val messageData = chatMessages.getOrPut(node) {
            ChatMessageData(
                node,
                authorEntity,
                ComponentRenderUtils.wrapComponents(
                    engineMessage.text.parseMiniMessageClient(),
                    client.options.chatBubbleLineWidth,
                    MinecraftClient.font
                ),
                engineMessage
            )
        }
        val visible = isMessageVisible(engineMessage, spy, chatBar)

        val data = ChatLineData(chatHudLine, isFirst, isLast, messageData)

        chatLines[chatHudLine] = data
        if (!isRepeat) { allMessages.add(0, data) }
        return (!visible || isRepeat)
    }

    fun deleteGuiMessage(line: GuiMessage.Line) {
        val data = chatLines.remove(line) ?: return
        chatMessages.remove(data.message.node)
        allMessages.remove(data)
    }

    fun deleteMessage(chatMessage: AcceptedMessage) {
        val messages = chatLines.values.filter { it.message.engineMessage.id == chatMessage.id }
        val accessor = chatHud as ChatHudAccessor
        contentToEngineMessages.remove(chatMessage.display)
        messages.forEach { message ->
            val line = message.line
            accessor.`engine$getVisibleMessages`().remove(line)
            accessor.`engine$getMessages`().remove(message.message.node)
            deleteGuiMessage(line)
        }
    }

    fun storeMessageContent(content: String, ticks: Int, message: AcceptedMessage): Boolean {
        val identity = MessageIdentity(ticks, content)
        if (identityContentToEngineMessages.contains(identity)) {
            return false
        }
        contentToEngineMessages[content] = message
        identityContentToEngineMessages[identity] = message
        return true
    }

    fun shouldRenderDebugInfo() = client.developerMode

    fun clearChatData() {
        chatLines.clear()
        chatMessages.clear()
        contentToEngineMessages.clear()
        identityContentToEngineMessages.clear()
        allMessages.clear()
        selectedMessage = null
        typingPlayers.clear()
    }

    /* FIXME: Последние строки сообщений дублируются, если это компоненты с какой-либо логикой. Поведение было замечено у достижений. */
    override fun onMessageAdd(message: AcceptedMessage) {
        val displayText = message.display.parseMiniMessageClient()
        val added = storeMessageContent(displayText.string, MinecraftClient.gui.guiTicks, message)
        if (added && !message.isVanilla) {
            chatHud.addMessage(displayText)
        }
    }

    override fun onMessageDelete(message: AcceptedMessage) {
        deleteMessage(message)
    }

    override fun onChannelEnable(channel: ClientChatChannel) {
        restore()
    }

    override fun onChannelDisable(channel: ClientChatChannel) {
        restore()
    }

    override fun onSpyEnable() {
        restore()
    }

    override fun onSpyDisable() {
        restore()
    }

    private fun restore() {
        val visibleMessages = (chatHud as ChatHudAccessor).`engine$getVisibleMessages`()
        visibleMessages.clear()
        visibleMessages.addAll(
            allMessages
                .filter { isMessageVisible(it.message.engineMessage, requireChatManager().spy, requireChatBar()) }
                .map { it.line }
        )
    }

    private fun requireChatBar() = chatManager?.chatBar ?: error("Chat bar is not available")

    private fun requireChatManager() = chatManager ?: error("Chat is not available")

    override fun onSettingsUpdate(settings: ClientChatSettings, chatBar: ChatBar) {
        channelsBar.updateButtons(chatBar.sections)
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
}