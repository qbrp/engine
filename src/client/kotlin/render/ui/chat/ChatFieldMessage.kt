package org.lain.engine.client.render.ui.chat

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import org.lain.engine.client.chat.ClientMessage
import org.lain.engine.client.chat.SpeechContent
import org.lain.engine.client.chat.VoiceBlip
import org.lain.engine.client.chat.shouldDisplayMessage
import org.lain.engine.client.render.ui.ColorMc


class ChatFieldMessage(
    val message: ClientMessage,
    private val addedTick: Long,
    private val context: ChatContext,
    voiceBlip: VoiceBlip? = null
) {
    private val gameSession = context.hud.gameSession
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
    private var fade = MessageFade(addedTick)

    private fun computeWidth(): Int {
        val width = context.options.chatWidth.toFloat() - textLeft - maximumRepeatsTextWidth
        return width.coerceAtLeast(1f).toInt()
    }

    private val text = content.resolve(context.state)
    private val lines = text.lines(availableTextWidth)

    val height get() = lines.size * lineHeight + indent
    val visible
        get() = chatBar == null || shouldDisplayMessage(message, context.state.spy, chatBar)

    val voiceBlip: VoiceBlip? = voiceBlip ?: if (content is SpeechContent && visible) {
        gameSession.chatManager.voiceBlipManager.getOrCreate(message.id) {
            VoiceBlip.create(lines, content, message.source.author.player?.id!!)
        }
    } else {
        null
    }

    fun copy() = ChatFieldMessage(message, addedTick, context, voiceBlip).also {
        it.fade = fade.copy()
    }

    fun render(guiGraphics: GuiGraphics, alpha: Float) {
        val textAlpha = context.options.chatTextOpacity * alpha
        val textColor = ColorMc.color(textAlpha, CommonColors.WHITE)

        if (message.showHead) {
            drawHead(guiGraphics, textColor, textAlpha)
        }

        val speechShake = if (voiceBlip != null) {
            (content as? SpeechContent)?.let {
                it.randomScreamTextTranslation(context.hud.gameSession) *
                        (1f - (ticks - LIFETIME).coerceAtLeast(0f) / 40f)
            } ?: 1f
        } else {
            0f
        }

        if (voiceBlip != null && (voiceBlip.active || speechShake > 1f)) {
            guiGraphics.drawTypewriterString(
                font,
                lines,
                voiceBlip.node.chars,
                voiceBlip.visibleSymbols(),
                textLeft,
                textTop,
                textColor,
                speechShake,
            )
        } else {
            lines.forEachIndexed { index, line ->
                guiGraphics.drawChatString(font, line, textLeft, textTop + (lineHeight * index), textColor)
            }
        }

        if (message.repeat > 1) {
            val repeatsString = if (message.repeat > 999) MAXIMUM_REPEATS_TEXT else "x${message.repeat}"
            val repeatsText = Component.literal(repeatsString).withStyle(ChatFormatting.GOLD)
            val repeatsX = context.options.chatWidth.toFloat() - font.width(repeatsText).toFloat()
            guiGraphics.drawChatString(
                font,
                repeatsText.visualOrderText,
                repeatsX,
                textTop + (lineHeight * lines.size),
                textColor
            )
        }
    }

    fun hit(localX: Float, localY: Float): Hit? {
        val textX = localX - textLeft
        val textY = localY - textTop
        if (textX < 0f || textY < 0f) return null
        val line = lines.getOrNull((textY / lineHeight).toInt()) ?: return null
        return font.splitter.componentStyleAtWidth(line, textX.toInt())?.let { Hit(it, this) }
    }

    fun updateFade(canFade: Boolean) = fade.update(canFade, context.state.tick)

    fun calculateAlpha(): Float = fade.alpha(context.state.tick, LIFETIME, context.hud.fadeMessages)

    fun blocksFade() = voiceBlip != null && voiceBlip.active

    private fun drawHead(guiGraphics: GuiGraphics, textColor: Int, textAlpha: Float) {
        val shadowColor = ColorMc.color(textAlpha, 0xFF505050.toInt())
        val skin = resolvePlayerSkin()
        skin?.let {
            drawChatFace(guiGraphics, skin, 1f, 0f, shadowColor)
            drawChatFace(guiGraphics, skin, 2f, 1f, textColor)
        }
    }

    private fun resolvePlayerSkin() =
        message.source.player
            ?.let { gameSession.skinSystem.get(it.id.value) }

    companion object {
        const val LIFETIME = 200f
        const val MAXIMUM_REPEATS_TEXT = "x999+"
        const val AFTER_HEAD_INDENT = 2f
    }
}
