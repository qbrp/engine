package org.lain.engine.client.render.ui.chat

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import org.lain.engine.client.chat.ClientMessage
import org.lain.engine.client.chat.ServiceContent
import org.lain.engine.client.chat.SpeechContent
import org.lain.engine.client.chat.VoiceBlip
import org.lain.engine.client.chat.isMessageVisible
import org.lain.engine.client.render.ui.ColorMc


class ChatFieldMessage(
    val message: ClientMessage,
    private val addedTick: Long,
    private val context: ChatContext,
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

    private val ticks
        get() = context.state.tick - addedTick

    private fun computeWidth(): Int {
        val width = context.options.chatWidth.toFloat() - textLeft - maximumRepeatsTextWidth
        return width.coerceAtLeast(1f).toInt()
    }

    private val lines = content.resolve(context.state).lines(availableTextWidth)
    val height get() = lines.size * lineHeight + indent
    val visible
        get() = chatBar == null || isMessageVisible(message, context.state.spy, chatBar)

    var voiceBlipLifetime = 0f
    val voiceBlip: VoiceBlip? = if (content is SpeechContent && visible) {
        context.hud.gameSession.chatManager.voiceBlipManager.getOrCreate(message.id) {
            VoiceBlip.create(lines, content, message.source.author.player?.id!!)
        }
    } else {
        null
    }

    fun copy() = ChatFieldMessage(message, addedTick, context)

    fun render(guiGraphics: GuiGraphics, isHovered: Boolean, dt: Float) {
        val alpha = calculateAlpha()
        val textAlpha = context.options.chatTextOpacity * alpha
        val textColor = ColorMc.color(textAlpha, CommonColors.WHITE)
        var currentY = textTop

        if (voiceBlip != null && !voiceBlip.ended) {
            voiceBlipLifetime += dt
        }

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
                    val blip = voiceBlip
                    val node = blip?.nodes?.getOrNull(index)
                    if (blip == null || node == null) {
                        drawString(line)
                    } else {
                        guiGraphics.drawTypewriterString(
                            font,
                            line,
                            node.chars,
                            blip.visibleSymbols(index),
                            textLeft,
                            currentY,
                            textColor,
                            content.randomScreamTextTranslation(context.hud.gameSession) *
                                    (1f - (ticks - LIFETIME).coerceAtLeast(0f) / 40f),
                        )
                    }
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
        if (voiceBlip != null && !voiceBlip.ended) return 1f
        if (!context.hud.fadeMessages) return 1f
        var d = ((ticks - voiceBlipLifetime).coerceAtLeast(0f) / LIFETIME).toDouble()
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
        const val LIFETIME = 200f
        const val MAXIMUM_REPEATS_TEXT = "x999+"
        const val AFTER_HEAD_INDENT = 2f
    }
}
