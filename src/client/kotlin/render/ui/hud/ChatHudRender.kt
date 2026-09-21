package org.lain.engine.client.render.ui.hud

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import org.lwjgl.glfw.GLFW
import org.lain.engine.client.mc.chat.MinecraftChat
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.mc.Text
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.util.Color

const val CHAT_HEAD_SIZE = 8

open class ShakingTextFieldWidget(private val textRenderer: Font, x: Int, y: Int, width: Int, height: Int, text: Text) : EditBox(
    textRenderer, x, y, width, height, text
) {
    private var channelAnim = 0f
    private var channelAnimTarget = 0f
    private var channelText: Text = "Канал".parseMiniMessageClient()
    private var channelTextWidth = 0
    private var fadeOut = false

    private fun updateChannel(channel: ClientChatChannel?) {
        if (channel == null) {
            fadeOut = true
        } else {
            this.channelText = channel.name.parseMiniMessageClient()
        }
        channelTextWidth = if (channel != null) {
            textRenderer.width(this.channelText) + 4
        } else {
            0
        }
        channelAnimTarget = if (channel != null) 1f else 0f
    }

    override fun getInnerWidth(): Int {
        return super.getInnerWidth() - channelTextWidth
    }

    private fun tickAnimation(delta: Float) {
        val speed = 0.5f
        channelAnim += (channelAnimTarget - channelAnim) * speed * delta
        channelAnim = channelAnim.coerceIn(0f, 1f)
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        tickAnimation(deltaTicks)
        context.pose().pushPose()
        context.pose().translate(
            MinecraftChat.getRandomShakeTranslation().toDouble(),
            MinecraftChat.getRandomShakeTranslation().toDouble(),
            0.0
        )
        super.renderWidget(context, mouseX, mouseY, deltaTicks)
        context.pose().popPose()

        if (channelAnim > 0.01f) {
            val textWidth = textRenderer.width(channelText)

            val alpha = (channelAnim * 255).toInt().coerceIn(0, 255)
            val color = Color.of(alpha, 160, 160, 160).integer

            context.drawString(
                textRenderer,
                channelText,
                x + width - textWidth - 6,
                y,
                color
            )
        }
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return if (super.charTyped(codePoint, modifiers)) {
            updateChannel(MinecraftChat.chatManager?.onTextInput(this.value))
            MinecraftChat.updateChatInput(value)
            true
        } else {
            false
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val result = super.keyPressed(keyCode, scanCode, modifiers)
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
            updateChannel(MinecraftChat.chatManager?.onTextInput(this.value))
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            updateChannel(null)
        }
        return result
    }
}
