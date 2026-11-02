package org.lain.engine.client.render.ui.chat

import net.minecraft.client.GuiMessageTag
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.player.ChatVisiblity
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.chat.SYSTEM_CHANNEL
import org.lain.engine.client.chat.VanillaText
import org.lain.engine.client.chat.getSystemClient
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.chat.MinecraftChat.chatManager
import org.lain.engine.client.mixin.chat.ChatHudAccessor

class ChatHudWrapper(chatHud: ChatComponent) {
    private val access = chatHud as ChatHudAccessor
    private val minecraft get() = access.`engine$getMinecraft`()
    private val hud get() = chatManager?.hud

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, focused: Boolean) {
        if (isChatHidden()) return
        hud?.render(
            guiGraphics,
            mouseX.toFloat(),
            mouseY.toFloat(),
            minecraft.timer.realtimeDeltaTicks,
            focused || ClientMixin.shouldFocusChatIfNot(),
        )
    }

    fun getClickedComponentStyleAt(mouseX: Double, mouseY: Double): Style? {
        if (isChatHidden() || minecraft.options.hideGui) return null
        return hud?.hit(mouseX.toFloat(), mouseY.toFloat())?.style
    }

    fun selectMessage(mouseX: Double, mouseY: Double): Boolean {
        val clicked = hud?.messageAt(mouseX.toFloat(), mouseY.toFloat())?.message
        hud?.selectedMessage = clicked
        return clicked != null
    }

    fun scrollChat(amount: Int): Boolean {
        val hud = hud ?: return false
        hud.scroll(amount.toFloat())
        return true
    }

    fun resetScroll() {
        hud?.resetScroll()
    }

    fun storeVanillaMessage(component: Component, signature: MessageSignature?, tag: GuiMessageTag?) {
        val chatManager = chatManager ?: return
        val contentText = VanillaText(component)
        val engineMessage = AcceptedMessage(
            contentText,
            contentText,
            SYSTEM_CHANNEL,
            MessageSource.getSystemClient(chatManager.gameSession.world),
            id = MessageId.next(),
        )
        chatManager.addMessage(engineMessage)
    }

    private fun isChatHidden() = minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN
}
