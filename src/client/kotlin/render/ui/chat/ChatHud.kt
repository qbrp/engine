package org.lain.engine.client.render.ui.chat

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.CommonColors
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import org.lain.engine.chat.MessageId
import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.ClientMessage
import org.lain.engine.client.chat.CHAR_SHAKE_STOP_DELAY
import org.lain.engine.client.chat.ChatBar
import org.lain.engine.client.chat.MessageText
import org.lain.engine.client.chat.ServiceContent
import org.lain.engine.client.chat.SpeechContent
import org.lain.engine.client.chat.TypewriterEffect
import org.lain.engine.client.chat.TypingPlayer
import org.lain.engine.client.chat.isMessageVisible
import org.lain.engine.client.chat.toChars
import org.lain.engine.client.chat.toSyllables
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.isControlDown
import org.lain.engine.client.mc.isKeyDown
import org.lain.engine.client.render.ui.ColorMc
import org.lain.engine.client.render.ui.fill
import org.lain.engine.util.math.lerp
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

interface ChatOptions {
    val chatBackgroundOpacity: Float
    val chatTextOpacity: Float
    val activeChatPx: Int
    val unactiveChatPx: Int
    val chatWidth: Int
    val chatSize: Int
    val chatMessageIndent: Float
}

interface ChatState {
    val allhear: Boolean
    val tick: Long
    val spy: Boolean
    val chatBar: ChatBar?
}

data class ChatContext(
    val state: ChatState,
    val options: ChatOptions,
    val font: Font,
    val hud: ChatHud,
) {
    val packetListener: ClientPacketListener
        get() = MinecraftClient.player?.connection ?: error("Packet listener not started")
}

data class Hit(val style: Style, val message: ChatHudMessage)

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
        if (!context.hud.fadeMessages || typewriterEffect?.ended != true) return 1f
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

class ChatHud(val gameSession: GameSession) {
    private val client get() = gameSession.client
    private val options get() = client.options
    private val width get() = options.chatWidth.toFloat()
    private val window = client.window
    private val chatManager = gameSession.chatManager

    private val context = ChatContext(chatManager, options, MessageText.FONT, this)

    private val messages = mutableListOf<ChatHudMessage>()
    private val addedTicks = mutableMapOf<MessageId, Long>()
    private val visibleMessages = mutableListOf<VisibleMessage>()

    private class VisibleMessage(
        val message: ChatHudMessage,
        val topY: Float,
        val bottomY: Float
    )

    private var totalHeight = 0f
    private var scrollY = 0f
    private var targetScrollY = 0f
    private var focused = false
    private var viewportTop = 0f
    private var viewportBottom = 0f
    var selectedMessage: ClientMessage? = null

    val fadeMessages get() = !focused

    fun scroll(dt: Float) {
        if (messages.isEmpty() || dt == 0f) return

        val maxScrollY = (totalHeight - resolveAreaHeight())
            .coerceAtLeast(0f)

        targetScrollY = (targetScrollY + dt * 10f)
            .coerceIn(0f, maxScrollY)
    }

    fun addMessage(message: ClientMessage) {
        addMessage(ChatHudMessage(message, gameSession.ticks, context))
    }

    fun addMessage(message: ChatHudMessage) {
        messages.addFirst(message)
        totalHeight += message.height

        if (targetScrollY > 0f) {
            scrollY += message.height
            targetScrollY += message.height
        }

        if (messages.size > options.chatSize) {
            totalHeight -= messages.removeAt(messages.size - 1).height
        }
    }

    fun removeMessage(id: MessageId) {
        val index = messages.indexOfFirst { it.message.id == id }
        if (index >= 0) {
            totalHeight -= messages.removeAt(index).height
        }
        addedTicks.remove(id)
        visibleMessages.clear()
        if (selectedMessage?.id == id) selectedMessage = null
    }

    fun clearMessages() {
        messages.clear()
        addedTicks.clear()
        visibleMessages.clear()
        totalHeight = 0f
        selectedMessage = null
        resetScroll()
    }

    fun invalidateMessages() {
        val hudMessages = this.messages.reversed()
        clearMessages()
        hudMessages.forEach { it.copy() }
    }

    fun resetScroll() {
        scrollY = 0f
        targetScrollY = 0f
        visibleMessages.clear()
    }

    fun messageAt(mouseX: Float, mouseY: Float): ChatHudMessage? {
        if (!focused || mouseX < 0f || mouseX >= width || mouseY < viewportTop || mouseY >= viewportBottom) return null
        return visibleMessages.firstOrNull { mouseY >= it.topY && mouseY < it.bottomY }?.message
    }

    fun hit(mouseX: Float, mouseY: Float): Hit? {
        val message = messageAt(mouseX, mouseY) ?: return null
        return visibleMessages
            .firstOrNull { it.message === message }
            ?.let { it.message.hit(mouseX, mouseY - it.topY) }
    }

    fun render(
        guiGraphics: GuiGraphics,
        mouseX: Float,
        mouseY: Float,
        dt: Float,
        focused: Boolean = MinecraftClient.screen is ChatScreen,
    ) {
        this.focused = focused
        val bottomPadding = 32f
        val windowHeight = window.heightDp
        val areaHeight = resolveAreaHeight()
        val typingPlayers = chatManager.typingPlayers.list()
        val typingHeight = if (typingPlayers.isEmpty()) 0f else context.font.lineHeight.toFloat()
        viewportBottom = windowHeight - bottomPadding - typingHeight
        viewportTop = viewportBottom - areaHeight

        val maxScrollY = (totalHeight - areaHeight).coerceAtLeast(0f)

        targetScrollY = targetScrollY.coerceIn(0f, maxScrollY)
        scrollY = scrollY.coerceIn(0f, maxScrollY)

        scrollY = lerp(scrollY, targetScrollY, 1f - 0.2f.pow(dt))

        if (abs(scrollY - targetScrollY) < 0.01f) {
            scrollY = targetScrollY
        }

        if (focused) {
            guiGraphics.enableScissor(
                0,
                floor(viewportTop.toDouble()).toInt(),
                ceil(width.toDouble()).toInt(),
                ceil(viewportBottom.toDouble()).toInt(),
            )
        }
        guiGraphics.pose().pushPose()

        var currentY = viewportBottom + scrollY

        visibleMessages.clear()
        for (i in messages.indices) {
            val message = messages[i]
            if (!message.visible) continue

            val height = message.height
            val topY = currentY - height
            val alpha = message.calculateAlpha()

            if (currentY < viewportTop) {
                break
            }

            if (topY < viewportBottom && currentY > viewportTop && alpha * options.chatTextOpacity >= 4f / 255f) {
                val isHovered =
                    mouseX in 0f..<width &&
                            mouseY >= topY &&
                            mouseY < currentY
                val isSelected = selectedMessage?.id == message.message.id

                guiGraphics.pose().pushPose()
                guiGraphics.pose().translate(0f, topY, 0f)

                guiGraphics.fill(
                    0f, 0f, width, height,
                    ColorMc.color(
                        alpha * options.chatBackgroundOpacity,
                        message.message.backgroundColorInt ?: CommonColors.BLACK
                    )
                )
                message.render(guiGraphics, isHovered, dt)
                visibleMessages += VisibleMessage(message, topY, topY + height)

                if (focused && isHovered) guiGraphics.fill(0f, 0f, width, height, ColorMc.color(50, CommonColors.WHITE))
                if (isSelected) guiGraphics.fill(0f, 0f, width, height, ColorMc.color(50, CommonColors.WHITE))
                if (isSelected && isControlDown() && isKeyDown(GLFW.GLFW_KEY_C)) {
                    guiGraphics.fill(0f, 0f, width, height, ColorMc.color(50, CommonColors.WHITE))
                }

                guiGraphics.pose().popPose()
            }

            currentY = topY
        }

        guiGraphics.pose().popPose()
        if (focused) guiGraphics.disableScissor()
        renderTypingPlayers(guiGraphics, typingPlayers)
        renderScrollbar(guiGraphics, areaHeight, maxScrollY)
    }

    private fun renderTypingPlayers(guiGraphics: GuiGraphics, players: Collection<TypingPlayer>) {
        if (players.isEmpty()) return
        val y = viewportBottom
        val lineHeight = context.font.lineHeight.toFloat()
        guiGraphics.fill(0f, y, width, y + lineHeight, ColorMc.color(options.chatBackgroundOpacity, CommonColors.BLACK))
        var x = 2f
        players.forEachIndexed { index, player ->
            player.skin?.let { skin -> drawChatFace(guiGraphics, skin, x, y, CommonColors.WHITE) }
            x += CHAT_HEAD_SIZE.toFloat() + 2f
            guiGraphics.drawChatString(context.font, player.name.visualOrderText, x, y, CommonColors.WHITE)
            x += context.font.width(player.name).toFloat()
            if (index < players.size - 1) {
                guiGraphics.drawChatString(
                    context.font,
                    Component.literal(",").visualOrderText,
                    x,
                    y,
                    CommonColors.LIGHT_GRAY
                )
                x += 4f
            }
            x += 4f
        }
        guiGraphics.drawChatString(
            context.font,
            Component.literal("печатает...").visualOrderText,
            x,
            y,
            CommonColors.LIGHT_GRAY,
        )
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, areaHeight: Float, maxScrollY: Float) {
        if (!focused || maxScrollY <= 0f || areaHeight <= 0f) return
        val height = (areaHeight * areaHeight / totalHeight).coerceIn(1f, areaHeight)
        val top = viewportBottom - height - scrollY / maxScrollY * (areaHeight - height)
        guiGraphics.fill(
            width,
            viewportTop,
            width + 4f,
            viewportBottom,
            ColorMc.color(options.chatBackgroundOpacity, CommonColors.BLACK)
        )
        guiGraphics.fill(width + 2f, top, width + 4f, top + height, 0xAA808080.toInt())
    }

    fun resolveAreaHeight(): Float = (if (focused) options.activeChatPx else options.unactiveChatPx).toFloat()
}

private fun GuiGraphics.drawChatString(
    font: Font,
    text: FormattedCharSequence,
    x: Float,
    y: Float,
    color: Int,
): Int {
    pose().pushPose()
    try {
        pose().translate(x, y, 0f)
        return drawString(font, text, 0, 0, color)
    } finally {
        pose().popPose()
    }
}

private fun GuiGraphics.drawTypewriterString(
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

private fun randomShakeOffset(amplitude: Float): Float {
    if (amplitude <= 0f) return 0f
    return (Random.nextFloat() * 2f - 1f) * amplitude
}

private fun drawChatFace(guiGraphics: GuiGraphics, skin: PlayerSkin, x: Float, y: Float, color: Int) {
    guiGraphics.pose().pushPose()
    try {
        guiGraphics.pose().translate(x, y, 0f)
        guiGraphics.setColor(
            (color shr 16 and 0xFF) / 255f,
            (color shr 8 and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            (color ushr 24) / 255f,
        )
        RenderSystem.enableBlend()
        PlayerFaceRenderer.draw(guiGraphics, skin, 0, 0, CHAT_HEAD_SIZE)
    } finally {
        guiGraphics.setColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        guiGraphics.pose().popPose()
    }
}
