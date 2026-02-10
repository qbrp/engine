package org.lain.engine.client.mc.render

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.ChatHudLine.Visible
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lain.engine.client.mc.MinecraftChat
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.util.Color

// Зачем нужна вся эта хуйня? А потому-что инкапсуляция, сученька, всё скрываем, ООП нахуй

fun interface EngineLineConsumer {
    fun accept(var1: Int, var2: Int, var3: Int, var4: Visible?, var5: Int, var6: Float)
}

fun interface MessageOpacityMultiplierProvider {
    fun getMessageOpacityMultiplier(age: Int): Float
}

const val LINE_INDENT = 8

fun forEachVisibleLine(
    visibleLineCount: Int,
    currentTick: Int,
    focused: Boolean,
    windowHeight: Int,
    lineHeight: Int,
    visibleMessages: List<Visible>,
    scrolledLines: Int,
    multiplierProvider: MessageOpacityMultiplierProvider,
    consumer: EngineLineConsumer,
): Int {
    var j = 0
    for (k in (visibleMessages.size - scrolledLines).coerceAtMost(visibleLineCount) - 1 downTo 0) {
        val f: Float
        val l: Int = k + scrolledLines
        val visible: Visible? = visibleMessages[l]
        if (visible == null) continue
        val m = currentTick - visible.addedTime()
        f = if (focused) 1.0f else multiplierProvider.getMessageOpacityMultiplier(m).toFloat()
        if (!(f > 1.0E-5f)) continue
        ++j
        val n = windowHeight - k * lineHeight
        val o = n - lineHeight
        consumer.accept(0, o, n, visible, k, f)
    }
    return j
}

open class ShakingTextFieldWidget(private val textRenderer: TextRenderer, x: Int, y: Int, width: Int, height: Int, text: Text) : TextFieldWidget(textRenderer, x, y, width, height, text) {
    private var channelAnim = 0f
    private var channelAnimTarget = 0f
    private var channelText: Text? = null
    private var channelTextWidth = 0
    private var fadeOut = false

    private fun updateChannel(channel: ClientChatChannel?) {
        if (channel == null) {
            fadeOut = true
        } else {
            this.channelText = channel.name.parseMiniMessageClient()
        }
        channelTextWidth = if (channel != null) {
            textRenderer.getWidth(this.channelText) + 4
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

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        tickAnimation(deltaTicks)
        context.matrices.pushMatrix()
        context.matrices.translate(MinecraftChat.getRandomShakeTranslation(), MinecraftChat.getRandomShakeTranslation())
        super.renderWidget(context, mouseX, mouseY, deltaTicks)
        context.matrices.popMatrix()

        if (channelText != null && channelAnim > 0.01f) {
            val textWidth = textRenderer.getWidth(channelText)

            val alpha = (channelAnim * 255).toInt().coerceIn(0, 255)
            val color = Color.of(alpha, 160, 160, 160).integer

            context.drawTextWithShadow(
                textRenderer,
                channelText,
                x + width - textWidth - 6,
                y,
                color
            )
        }
    }

    override fun charTyped(input: CharInput?): Boolean {
        return if (super.charTyped(input)) {
            updateChannel(MinecraftChat.chatManager?.onTextInput(this.text))
            true
        } else {
            false
        }
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val result = super.keyPressed(input)
        if (input.key == 259 || input.key == 261) {
            updateChannel(MinecraftChat.chatManager?.onTextInput(this.text))
        } else if (input.isEnter) {
            updateChannel(null)
        }
        return result
    }
}