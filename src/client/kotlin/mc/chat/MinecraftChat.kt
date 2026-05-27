package org.lain.engine.client.mc.chat

import net.minecraft.client.GuiMessage
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.entity.player.PlayerSkin
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.client.chat.*
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.injectClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.client.mixin.chat.ChatHudAccessor
import org.lain.engine.client.render.ui.ChatChannelsBar
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.mc.Text
import org.lain.engine.mc.displayNameMiniMessage
import org.lain.engine.player.PlayerId
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT
import org.lain.engine.transport.packet.ClientChatSettings
import org.lain.engine.util.HIGH_VOLUME_COLOR
import org.lain.engine.util.LOW_VOLUME_COLOR
import org.lain.engine.util.math.lerp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

object MinecraftChat : ChatEventBus {
    private val client by injectClient()

    val messages = mutableListOf<EngineChatHudMessage>()
    val visibleMessages = mutableListOf<EngineChatHudLine>()

    private val chatHud get() =  MinecraftClient.gui.chat
    private val chatHudAccessor get() = chatHud as ChatHudAccessor
    private val chatWidth get() = floor(chatHudAccessor.`engine$getWidth`() / chatHudAccessor.`engine$getScale`()).toInt()
    private val spy get() = chatManager?.spy ?: false
    val chatManager get() = client.gameSession?.chatManager

    var selectedMessage: EngineChatHudMessage? = null
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

    fun onCloseChatInput() {
        chatManager?.endTyping()
    }

    private fun createChatHudMessage(engineMessage: AcceptedMessage, guiMessage: GuiMessage): EngineChatHudMessage {
        val author = engineMessage.source.player
        val authorEntity = author?.let { MinecraftClient.connection?.getPlayerInfo(it.id.value) }
        return EngineChatHudMessage(
            guiMessage,
            authorEntity,
            engineMessage,
            guiMessage.addedTime
        )
    }

    fun storeVanillaGuiMessage(guiMessage: GuiMessage) {
        val content = guiMessage.content.string
        val engineMessage = AcceptedMessage(
            content,
            content,
            content,
            SYSTEM_CHANNEL,
            MessageSource.getSystemClient(client.gameSession?.world ?: DummyWorld()),
            id = MessageId.next(),
        )
        chatManager?.addMessage(engineMessage)
    }

    fun deleteMessage(chatMessage: AcceptedMessage) {
        val deletedMessage = messages
            .firstOrNull { it.engineMessage.id == chatMessage.id }

        deletedMessage?.let {
            messages.remove(it)
            visibleMessages.removeIf { visible -> visible.message == it }
        }
    }

    fun shouldRenderDebugInfo() = client.developerMode

    fun clearChatData() {
        messages.clear()
        selectedMessage = null
        typingPlayers.clear()
    }

    override fun addToGui(message: AcceptedMessage) {
        val allhear = chatManager?.allhear ?: false
        val visibleText = when(allhear) {
            true -> message.undistortedDisplayText
            false -> message.displayText
        }
        val parsedText = visibleText.parseMiniMessageClient()
        val addedTime = MinecraftClient.gui.guiTicks

        val messageData = createChatHudMessage(
            message,
            GuiMessage(addedTime, parsedText, null, null)
        )
        messages.addFirst(messageData)
        chatHud.`engine$addMessage`(messageData, false)
        if (messages.size > client.options.chatFieldSize) {
            messages.removeLast()
        }
    }

    override fun onMessageDelete(message: AcceptedMessage) {
        deleteMessage(message)
    }

    override fun invalidateChatEntries() { restore() }

    private fun restore() {
        visibleMessages.clear()
        messages
            .filter { isMessageVisible(it.engineMessage, requireChatManager().spy, requireChatBar()) }
            .reversed()
            .forEach { chatHud.`engine$addMessage`(it, false) }
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