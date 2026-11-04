package org.lain.engine.client.render.ui.chat

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.FormattedCharSequence
import org.lain.engine.client.chat.CHAR_SHAKE_STOP_DELAY
import org.lain.engine.client.chat.MAX_CHAR_SHAKE
import org.lain.engine.client.chat.SyllableChar
import kotlin.random.Random

fun GuiGraphics.drawTypewriterString(
    font: Font,
    text: FormattedCharSequence,
    line: List<SyllableChar>,
    visibleSymbols: Int,
    x: Float,
    y: Float,
    color: Int,
    shakeBonus: Float
) {
    if (visibleSymbols <= 0) return

    var symbolIndex = 0
    var currentX = x
    text.accept { _, style, codePoint ->
        if (symbolIndex >= visibleSymbols) {
            false
        } else {
            val symbol = FormattedCharSequence.codepoint(codePoint, style)
            val char = line.getOrNull(symbolIndex)
            val shake = char?.let { shakeBonus + it.shake }?.takeIf { char.parent.shake } ?: 0f
            drawChatString(
                font,
                symbol,
                currentX + randomShakeOffset(shake),
                y + randomShakeOffset(shake),
                color,
            )
            currentX += font.width(symbol).toFloat()
            symbolIndex++
            symbolIndex < visibleSymbols
        }
    }
}

fun randomShakeOffset(amplitude: Float): Float {
    if (amplitude <= 0f) return 0f
    return (Random.nextFloat() * 2f - 1f) * amplitude
}

val SyllableChar.shake: Float
    get() {
        if (delay <= CHAR_SHAKE_STOP_DELAY) return 0f

        val duration = parent.charDur - CHAR_SHAKE_STOP_DELAY
        val progress = ((delay - CHAR_SHAKE_STOP_DELAY) / duration).coerceIn(0f, 1f)
        return MAX_CHAR_SHAKE * progress
    }
