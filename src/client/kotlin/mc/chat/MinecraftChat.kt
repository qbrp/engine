package org.lain.engine.client.mc.chat

import org.lain.engine.client.chat.*
import org.lain.engine.client.mc.injectClient
import org.lain.engine.client.render.ui.chat.computeBaseShake
import org.lain.engine.util.HIGH_VOLUME_COLOR
import org.lain.engine.util.LOW_VOLUME_COLOR
import org.lain.engine.util.math.lerp
import kotlin.math.pow
import kotlin.random.Random

object MinecraftChat : ChatEventBus {
    val client by injectClient()

    val chatManager get() = client.gameSession?.chatManager
    val channelsBar get() = chatManager?.barUi

    private var textShakingBoost = 0f
    var isWritingCommand = false
    val chatInputTextShaking: Float get() {
        val volume = client.gameSession?.vocalRegulator?.volume ?: return  0f
        return if (isWritingCommand) 0f else volume.computeBaseShake(client.options) + textShakingBoost
    }

    fun clearMessages() {
        chatManager?.clearMessages()
    }

    fun deleteSelectedChatMessage() {
        chatManager?.hud?.selectedMessage?.let { chatManager?.deleteMessage(it.id) }
    }

    fun onChatScreenClose() {
        chatManager?.hud?.selectedMessage = null
    }

    fun selectedChatMessage(): ClientMessage? {
        return chatManager?.hud?.selectedMessage
    }

    fun onCloseChatInput() {
        chatManager?.endTyping()
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
