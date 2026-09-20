package org.lain.engine.client.render.ui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.ChatFormatting
import net.minecraft.client.GuiMessage
import net.minecraft.client.GuiMessageTag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.chat.Style
import net.minecraft.util.CommonColors
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.ChatVisiblity
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.chat.EngineAlphaCalculator
import org.lain.engine.client.mc.chat.EngineChatHudLine
import org.lain.engine.client.mc.chat.EngineChatHudMessage
import org.lain.engine.client.mc.chat.MAXIMUM_REPEATS_TEXT
import org.lain.engine.client.mc.chat.MinecraftChat
import org.lain.engine.client.mixin.chat.ChatHudAccessor
import org.lain.engine.client.render.ui.hud.CHAT_HEAD_SIZE

class ChatHudWrapper(chatHud: ChatComponent) {
    private val access = chatHud as ChatHudAccessor
    private val visibleMessages = MinecraftChat.visibleMessages

    private val minecraft: Minecraft
        get() = access.`engine$getMinecraft`()

    private val scrollbarPosition: Int
        get() = access.`engine$getChatScrollbarPos`()

    private var newMessageSinceScroll: Boolean
        get() = access.`engine$getNewMessageSinceScroll`()
        set(value) = access.`engine$setNewMessageSinceScroll`(value)

    fun indentWidth(width: Int): Int = width + CHAT_HEAD_SIZE

    fun render(guiGraphics: GuiGraphics, currentTick: Int, mouseX: Int, mouseY: Int, focused: Boolean) {
        if (isChatHidden()) return

        val chatFocused = focused || ClientMixin.shouldFocusChatIfNot()
        val typingPlayers = MinecraftChat.typingPlayers.toList()
        val queuedMessageCount = minecraft.getChatListener().queueSize()
        val totalLineCount = visibleMessages.size
        if (totalLineCount == 0 && typingPlayers.isEmpty() && queuedMessageCount == 0L) return

        minecraft.getProfiler().push("chat")

        val renderState = createRenderState(guiGraphics, chatFocused)
        val hoveredMessage = getMessageAt(mouseX.toDouble(), mouseY.toDouble(), chatFocused)
        val selectedMessage = MinecraftChat.selectedMessage

        guiGraphics.pose().pushPose()
        guiGraphics.pose().scale(renderState.scale, renderState.scale, 1f)
        guiGraphics.pose().translate(4f, 0f, 0f)

        renderTypingPlayers(guiGraphics, renderState, typingPlayers)
        val renderedLineCount = renderMessages(
            guiGraphics,
            renderState,
            currentTick,
            totalLineCount,
            hoveredMessage,
            selectedMessage
        )
        renderQueuedMessages(guiGraphics, renderState, queuedMessageCount)
        renderScrollbar(guiGraphics, renderState, totalLineCount, renderedLineCount)

        guiGraphics.pose().popPose()
        minecraft.getProfiler().pop()
    }

    fun getClickedComponentStyleAt(mouseX: Double, mouseY: Double): Style? {
        val lineIndex = getMessageLineIndexAt(mouseX, mouseY, true)
        if (lineIndex < 0) return null

        val chatX = mouseX / access.`engine$getScale`() - 4.0
        val textX = Mth.floor(chatX) - CHAT_HEAD_SIZE - 2
        if (textX < 0) return null

        val text = visibleMessages[lineIndex].text
        return minecraft.font.splitter.componentStyleAtWidth(text, textX)
    }

    fun addMessage(message: EngineChatHudMessage) {
        val chatWidth = Mth.floor(access.`engine$getWidth`() / access.`engine$getScale`())
        val lines = message.resolveLines(chatWidth)
        val focused = access.`engine$isChatFocused`()

        lines.forEach { line ->
            if (focused && scrollbarPosition > 0) {
                newMessageSinceScroll = true
                access.`engine$scrollChat`(1)
            }
            visibleMessages.add(0, line)
        }

        while (visibleMessages.size > ClientMixin.getChatSize()) {
            visibleMessages.removeLast()
        }
    }

    fun selectMessage(mouseX: Double, mouseY: Double): Boolean {
        val clickedMessage = getMessageAt(mouseX, mouseY, true)
        val selectedMessage = MinecraftChat.selectedMessage
        MinecraftChat.selectedMessage = if (clickedMessage != null && clickedMessage == selectedMessage) {
            null
        } else {
            clickedMessage
        }
        return clickedMessage != null
    }

    fun storeVanillaMessage(component: Component, signature: MessageSignature?, tag: GuiMessageTag?) {
        val guiMessage = GuiMessage(minecraft.gui.guiTicks, component, signature, tag)
        MinecraftChat.storeVanillaGuiMessage(guiMessage)
    }

    fun clearMessages() = MinecraftChat.clearChatData()

    fun rescaleChat() = MinecraftChat.invalidateChatEntries()

    fun getVisibleMessageCount(): Int = visibleMessages.size

    private fun createRenderState(guiGraphics: GuiGraphics, focused: Boolean): RenderState {
        val scale = access.`engine$getScale`().toFloat()
        val chatWidth = Mth.ceil(access.`engine$getWidth`() / scale)
        val chatBottomY = Mth.floor((guiGraphics.guiHeight() - 40) / scale)
        val textOpacity = minecraft.options.chatOpacity().get().toFloat() * 0.9f + 0.1f
        val backgroundOpacity = minecraft.options.textBackgroundOpacity().get().toFloat()
        val lineSpacing = minecraft.options.chatLineSpacing().get()
        val lineHeight = getLineHeight()
        val textOffsetY = Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing).toInt()
        return RenderState(
            focused,
            scale,
            chatWidth,
            chatBottomY,
            textOpacity,
            backgroundOpacity,
            lineHeight,
            textOffsetY
        )
    }

    private fun renderTypingPlayers(
        guiGraphics: GuiGraphics,
        state: RenderState,
        typingPlayers: List<MinecraftChat.TypingPlayer>
    ) {
        if (typingPlayers.isEmpty()) return

        guiGraphics.fill(
            -4,
            state.chatBottomY,
            state.chatWidth + 8,
            state.chatBottomY + state.lineHeight,
            withAlpha(state.backgroundOpacity, CommonColors.BLACK)
        )

        var textX = 0
        typingPlayers.forEachIndexed { index, player ->
            drawFace(guiGraphics, player.skinTextures, textX + 1, state.chatBottomY + 1, 0xFF505050.toInt())
            drawFace(guiGraphics, player.skinTextures, textX, state.chatBottomY, CommonColors.WHITE)
            textX += 10
            guiGraphics.drawString(minecraft.font, player.name, textX, state.chatBottomY + 1, CommonColors.WHITE)
            textX += minecraft.font.width(player.name)
            if (index < typingPlayers.lastIndex) {
                textX++
                guiGraphics.drawString(minecraft.font, ",", textX, state.chatBottomY + 1, CommonColors.LIGHT_GRAY)
            }
            textX += 4
        }
        guiGraphics.drawString(minecraft.font, "печатает...", textX, state.chatBottomY + 1, CommonColors.LIGHT_GRAY)
    }

    private fun renderMessages(
        guiGraphics: GuiGraphics,
        state: RenderState,
        currentTick: Int,
        totalLineCount: Int,
        hoveredMessage: EngineChatHudMessage?,
        selectedMessage: EngineChatHudMessage?
    ): Int {
        val alphaCalculator = if (state.focused) {
            EngineAlphaCalculator.FULLY_VISIBLE
        } else {
            EngineAlphaCalculator.timeBased(currentTick)
        }
        val pageLineCount = minOf(
            access.`engine$getLinesPerPage`(),
            maxOf(0, totalLineCount - scrollbarPosition)
        )
        var renderedLineCount = 0

        repeat(pageLineCount) { lineIndex ->
            val engineLine = visibleMessages[lineIndex + scrollbarPosition]
            val alpha = alphaCalculator.calculate(engineLine)
            if (getAlphaByte(alpha * state.textOpacity) < MINIMUM_TEXT_ALPHA) return@repeat

            renderedLineCount++
            renderMessageLine(guiGraphics, state, lineIndex, engineLine, alpha, hoveredMessage, selectedMessage)
        }

        return renderedLineCount
    }

    private fun renderMessageLine(
        guiGraphics: GuiGraphics,
        state: RenderState,
        lineIndex: Int,
        engineLine: EngineChatHudLine,
        alpha: Float,
        hoveredMessage: EngineChatHudMessage?,
        selectedMessage: EngineChatHudMessage?
    ) {
        val lineBottomY = state.chatBottomY - lineIndex * state.lineHeight
        val lineTopY = lineBottomY - state.lineHeight
        val textY = lineBottomY + state.textOffsetY
        val engineMessage = engineLine.message.engineMessage
        val backgroundColor = engineMessage.backgroundColorInt ?: CommonColors.BLACK

        guiGraphics.fill(
            -4,
            lineTopY,
            state.chatWidth + 8,
            lineBottomY,
            withAlpha(alpha * state.backgroundOpacity, backgroundColor)
        )

        val textColor = withAlpha(alpha * state.textOpacity, CommonColors.WHITE)
        guiGraphics.drawString(minecraft.font, engineLine.text, CHAT_HEAD_SIZE + 2, textY, textColor)
        renderMessageAuthor(guiGraphics, engineLine, engineMessage, textY, alpha, state.textOpacity, textColor)
        renderMessageMetadata(guiGraphics, state, engineLine, engineMessage, textY, alpha, textColor)
        renderMessageSelection(
            guiGraphics,
            state,
            engineLine.message,
            hoveredMessage,
            selectedMessage,
            lineTopY,
            lineBottomY
        )
    }

    private fun renderMessageAuthor(
        guiGraphics: GuiGraphics,
        line: EngineChatHudLine,
        message: AcceptedMessage,
        textY: Int,
        alpha: Float,
        textOpacity: Float,
        textColor: Int
    ) {
        val author = line.message.author ?: return
        if (!line.isFirst || !message.showHead) return

        val shadowColor = withAlpha(alpha * textOpacity, 0xFF505050.toInt())
        drawFace(guiGraphics, author.skin, 1, textY + 1, shadowColor)
        drawFace(guiGraphics, author.skin, 0, textY, textColor)
    }

    private fun renderMessageMetadata(
        guiGraphics: GuiGraphics,
        state: RenderState,
        line: EngineChatHudLine,
        message: AcceptedMessage,
        textY: Int,
        alpha: Float,
        textColor: Int
    ) {
        if (!line.isLast) return

        if (message.repeat > 1) {
            val repeatsString = if (message.repeat > 999) MAXIMUM_REPEATS_TEXT else "x${message.repeat}"
            val repeatsText = Component.literal(repeatsString).withStyle(ChatFormatting.GOLD)
            val repeatsX = state.chatWidth - minecraft.font.width(repeatsText)
            guiGraphics.drawString(minecraft.font, repeatsText, repeatsX, textY, textColor, true)
        }

        val debugText = line.message.debugText
        if (debugText != null && MinecraftChat.shouldRenderDebugInfo()) {
            val debugX = CHAT_HEAD_SIZE + 6 + minecraft.font.width(line.text)
            guiGraphics.drawString(
                minecraft.font,
                debugText,
                debugX,
                textY,
                withAlpha(alpha * state.textOpacity, CommonColors.GRAY)
            )
        }
    }

    private fun renderMessageSelection(
        guiGraphics: GuiGraphics,
        state: RenderState,
        message: EngineChatHudMessage,
        hoveredMessage: EngineChatHudMessage?,
        selectedMessage: EngineChatHudMessage?,
        lineTopY: Int,
        lineBottomY: Int
    ) {
        var selectionAlpha = 0
        if (message == hoveredMessage) selectionAlpha += 30
        if (message == selectedMessage) {
            selectionAlpha += 30
            if (ClientMixin.chatClipboardCopyTicksElapsed <= 2) selectionAlpha += 30
        }
        if (selectionAlpha == 0) return

        guiGraphics.fill(
            -4,
            lineTopY,
            state.chatWidth + 8,
            lineBottomY,
            withAlpha(selectionAlpha / 255f, CommonColors.WHITE)
        )
    }

    private fun renderQueuedMessages(guiGraphics: GuiGraphics, state: RenderState, queuedMessageCount: Long) {
        if (queuedMessageCount == 0L) return

        val queueTextAlpha = (128f * state.textOpacity).toInt()
        val queueBackgroundAlpha = (255f * state.backgroundOpacity).toInt()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0f, state.chatBottomY.toFloat(), 0f)
        guiGraphics.fill(-2, 0, state.chatWidth + 4, 9, queueBackgroundAlpha shl 24)
        guiGraphics.drawString(
            minecraft.font,
            Component.translatable("chat.queue", queuedMessageCount),
            0,
            1,
            withAlpha(queueTextAlpha / 255f, CommonColors.WHITE)
        )
        guiGraphics.pose().popPose()
    }

    private fun renderScrollbar(
        guiGraphics: GuiGraphics,
        state: RenderState,
        totalLineCount: Int,
        renderedLineCount: Int
    ) {
        if (!state.focused || totalLineCount == 0 || renderedLineCount == 0) return

        val totalPixelHeight = totalLineCount * state.lineHeight
        val visiblePixelHeight = renderedLineCount * state.lineHeight
        val scrollbarY = scrollbarPosition * visiblePixelHeight / totalLineCount - state.chatBottomY
        val scrollbarHeight = visiblePixelHeight * visiblePixelHeight / totalPixelHeight
        if (totalPixelHeight == visiblePixelHeight) return

        val scrollbarAlpha = if (scrollbarY > 0) 170 else 96
        val scrollbarColor = if (newMessageSinceScroll) 0xCC3333 else 0x3333AA
        val scrollbarX = state.chatWidth + 4
        guiGraphics.fill(
            scrollbarX,
            -scrollbarY,
            scrollbarX + 2,
            -scrollbarY - scrollbarHeight,
            withAlpha(scrollbarAlpha / 255f, scrollbarColor)
        )
        guiGraphics.fill(
            scrollbarX + 2,
            -scrollbarY,
            scrollbarX + 1,
            -scrollbarY - scrollbarHeight,
            withAlpha(scrollbarAlpha / 255f, 0xCCCCCC)
        )
    }

    private fun getMessageAt(mouseX: Double, mouseY: Double, focused: Boolean): EngineChatHudMessage? {
        val index = getMessageLineIndexAt(mouseX, mouseY, focused)
        return if (index >= 0) visibleMessages[index].message else null
    }

    private fun getMessageLineIndexAt(mouseX: Double, mouseY: Double, focused: Boolean): Int {
        if (!focused || !access.`engine$isChatFocused`() || minecraft.options.hideGui || isChatHidden()) return -1

        val scale = access.`engine$getScale`()
        val chatX = mouseX / scale - 4.0
        if (chatX < -4.0 || chatX > Mth.floor(access.`engine$getWidth`() / scale)) return -1

        val chatY = (minecraft.getWindow().guiScaledHeight - mouseY - 40.0) / (scale * getLineHeight())
        val visibleCount = minOf(access.`engine$getLinesPerPage`(), visibleMessages.size)
        if (chatY < 0.0 || chatY >= visibleCount) return -1

        val index = Mth.floor(chatY + scrollbarPosition)
        return index.takeIf { it in visibleMessages.indices } ?: -1
    }

    private fun isChatHidden(): Boolean {
        return minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN
    }

    private fun getLineHeight(): Int {
        return (9.0 * (minecraft.options.chatLineSpacing().get() + 1.0)).toInt()
    }

    private fun withAlpha(alpha: Float, color: Int): Int {
        return (getAlphaByte(alpha) shl 24) or (color and 0xFFFFFF)
    }

    private fun getAlphaByte(alpha: Float): Int {
        return Mth.clamp((alpha * 255f).toInt(), 0, 255)
    }

    private fun drawFace(guiGraphics: GuiGraphics, skin: PlayerSkin, x: Int, y: Int, color: Int) {
        guiGraphics.setColor(
            (color shr 16 and 0xFF) / 255f,
            (color shr 8 and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            (color ushr 24) / 255f
        )
        RenderSystem.enableBlend()
        PlayerFaceRenderer.draw(guiGraphics, skin, x, y, 8)
        guiGraphics.setColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    private data class RenderState(
        val focused: Boolean,
        val scale: Float,
        val chatWidth: Int,
        val chatBottomY: Int,
        val textOpacity: Float,
        val backgroundOpacity: Float,
        val lineHeight: Int,
        val textOffsetY: Int
    )

    companion object {
        private const val MINIMUM_TEXT_ALPHA = 4

        @JvmStatic
        fun calculateWidth(widthOption: Double): Int {
            return Mth.floor(widthOption * ClientMixin.getChatWidth() + 40)
        }
    }
}
