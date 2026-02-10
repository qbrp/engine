package org.lain.engine.client.mc.render

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.ChatHudLine.Visible
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.CharInput
import net.minecraft.text.Text
import org.lain.engine.client.mc.MinecraftChat

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

open class ShakingTextFieldWidget(textRenderer: TextRenderer, x: Int, y: Int, width: Int, height: Int, text: Text) : TextFieldWidget(textRenderer, x, y, width, height, text) {
    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.matrices.pushMatrix()
        context.matrices.translate(MinecraftChat.getRandomShakeTranslation(), MinecraftChat.getRandomShakeTranslation())
        super.renderWidget(context, mouseX, mouseY, deltaTicks)
        context.matrices.popMatrix()
    }

    override fun charTyped(input: CharInput?): Boolean {
        return if (super.charTyped(input)) {
            MinecraftChat.chatManager?.onCharTyped(this.text)
            true
        } else {
            false
        }
    }
}