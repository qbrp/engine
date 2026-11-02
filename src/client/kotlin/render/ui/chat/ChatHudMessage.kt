package org.lain.engine.client.render.ui.chat

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import org.lain.engine.client.chat.CHAR_SHAKE_STOP_DELAY
import org.lain.engine.client.chat.ClientMessage
import org.lain.engine.client.chat.ServiceContent
import org.lain.engine.client.chat.SpeechContent
import org.lain.engine.client.chat.TypewriterEffect
import org.lain.engine.client.chat.isMessageVisible
import org.lain.engine.client.chat.toChars
import org.lain.engine.client.chat.toSyllables
import org.lain.engine.client.render.ui.ColorMc
import kotlin.math.ceil
import kotlin.random.Random


class ChatHudMessage(
    val message: ClientMessage,
    private val addedTick: Long,
    private val context: ChatContext,
    typewriterEffect: TypewriterEffect? = null
) {
    private val chatBar = context.state.chatBar
    private val font = context.font
    private val content = message.content
    private val lineHeight = font.lineHeight.toFloat()

    private val textLeft = CHAT_HEAD_SIZE.toFloat() + 2f + AFTER_HEAD_INDENT
    private val textTop = 1f

    private val indent = context.options.chatMessageIndent
    private val maximumRepeatsTextWidth = font.width(MAXIMUM_REPEATS_TEXT).toFloat()
    private val availableTextWidth = computeWidth()

    private fun computeWidth(): Int {
        val width = context.options.chatWidth.toFloat() - textLeft - maximumRepeatsTextWidth
        return width.coerceAtLeast(1f).toInt()
    }

    private val lines = content.resolve(context.state).lines(availableTextWidth)
    val height get() = lines.size * lineHeight + indent
    val visible
        get() = chatBar == null || isMessageVisible(message, context.state.spy, chatBar)

    val typewriterEffect: TypewriterEffect? = typewriterEffect ?: if (content is SpeechContent) {
        val options = content.typewriterOptions
        TypewriterEffect(
            lines.mapIndexed { i, line ->
                val processedChars = if (i == 0) content.indent else 0
                val processedSyllables = if (processedChars > 0) 1 else 0
                val syllables = line.toSyllables(options.syllableDur, processedChars)
                TypewriterEffect.Line(
                    syllables,
                    syllables.toChars(processedSyllables),
                    processedSyllables,
                )
            },
            options
        )
    } else {
        null
    }

    fun copy() = ChatHudMessage(message, addedTick, context, typewriterEffect)

    fun render(guiGraphics: GuiGraphics, isHovered: Boolean, dt: Float) {
        val alpha = calculateAlpha()
        val textAlpha = context.options.chatTextOpacity * alpha
        val textColor = ColorMc.color(textAlpha, CommonColors.WHITE)
        var currentY = textTop

        if (typewriterEffect != null) {
            val activeLineIndex = typewriterEffect.lines.indexOfFirst { it.processed < it.syllables.size }
            if (activeLineIndex >= 0) {
                val line = typewriterEffect.lines[activeLineIndex]
                val syllable = line.syllables[line.processed]
                syllable.delay -= dt
                if (syllable.delay <= 0) {
                    line.processed++
                }
            }

            val lastStartedLine = if (activeLineIndex >= 0) activeLineIndex else typewriterEffect.lines.lastIndex
            for (lineIndex in 0..lastStartedLine) {
                typewriterEffect.lines[lineIndex].chars.forEach { char ->
                    if (char.delay > CHAR_SHAKE_STOP_DELAY) {
                        char.delay -= dt
                    }
                }
            }
        }

        val activeTypewriterLine = typewriterEffect?.lines?.indexOfFirst { it.processed < it.syllables.size } ?: -1

        lines.forEachIndexed { index, line ->
            if (index == 0 && message.showHead) {
                val shadowColor = ColorMc.color(textAlpha, 0xFF505050.toInt())
                val skin = resolvePlayerSkin()
                skin?.let {
                    drawChatFace(guiGraphics, skin, 1f, 0f, shadowColor)
                    drawChatFace(guiGraphics, skin, 2f, 1f, textColor)
                }
            }

            fun drawString(line: FormattedCharSequence, x: Int = 0) =
                guiGraphics.drawChatString(font, line, textLeft + x, currentY, textColor)

            when (content) {
                is ServiceContent -> drawString(line)
                is SpeechContent -> {
                    val typewriterEffect = typewriterEffect!!
                    val typewriterLine = typewriterEffect.lines[index]
                    val visibleSymbols = when {
                        activeTypewriterLine == -1 -> typewriterLine.chars.size
                        index < activeTypewriterLine -> typewriterLine.chars.size
                        index == activeTypewriterLine -> {
                            val syllable = typewriterLine.syllables[typewriterLine.processed]
                            val symbolCount = syllable.charsCount
                            val remaining = ceil(syllable.delay / syllable.charDur).toInt()
                            val visibleInSyllable = (symbolCount - remaining + 1).coerceIn(1, symbolCount)

                            syllable.startCharIndex + visibleInSyllable
                        }

                        else ->
                            0
                    }
                    guiGraphics.drawTypewriterString(
                        font,
                        line,
                        typewriterLine,
                        visibleSymbols,
                        textLeft,
                        currentY,
                        textColor,
                    )
                }
            }

            if (message.repeat > 1 && index == lines.size - 1) {
                val repeatsString = if (message.repeat > 999) MAXIMUM_REPEATS_TEXT else "x${message.repeat}"
                val repeatsText = Component.literal(repeatsString).withStyle(ChatFormatting.GOLD)
                val repeatsX = context.options.chatWidth.toFloat() - font.width(repeatsText).toFloat()
                guiGraphics.drawChatString(font, repeatsText.visualOrderText, repeatsX, currentY, textColor)
            }
            currentY += lineHeight
        }
    }

    fun hit(localX: Float, localY: Float): Hit? {
        val textX = localX - textLeft
        val textY = localY - textTop
        if (textX < 0f || textY < 0f) return null
        val line = lines.getOrNull((textY / lineHeight).toInt()) ?: return null
        return font.splitter.componentStyleAtWidth(line, textX.toInt())?.let { Hit(it, this) }
    }

    fun calculateAlpha(): Float {
        if (typewriterEffect != null && !typewriterEffect.ended) return 1f
        if (!context.hud.fadeMessages) return 1f
        val j = context.state.tick - addedTick
        var d = j / 200.0
        d = 1.0 - d
        d *= 10.0
        d = Mth.clamp(d, 0.0, 1.0)
        d *= d
        return d.toFloat()
    }

    private fun resolvePlayerSkin() =
        message.source.author.player
            ?.let { context.packetListener.getPlayerInfo(it.id.value) }
            ?.skin

    companion object {
        const val MAXIMUM_REPEATS_TEXT = "x999+"
        const val AFTER_HEAD_INDENT = 2f
    }
}

fun GuiGraphics.drawTypewriterString(
    font: Font,
    text: FormattedCharSequence,
    line: TypewriterEffect.Line,
    visibleSymbols: Int,
    x: Float,
    y: Float,
    color: Int,
) {
    if (visibleSymbols <= 0) return

    var symbolIndex = 0
    var currentX = x
    text.accept { _, style, codePoint ->
        if (symbolIndex >= visibleSymbols) {
            false
        } else {
            val symbol = FormattedCharSequence.codepoint(codePoint, style)
            val shake = line.chars.getOrNull(symbolIndex)?.shake ?: 0f
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
