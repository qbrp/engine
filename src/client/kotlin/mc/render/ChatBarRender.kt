package org.lain.engine.client.mc.render

import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import org.lain.engine.client.chat.ChatBarSection
import org.lain.engine.client.chat.ClientEngineChatManager
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.injectClient
import org.lain.engine.client.render.EXCLAMATION_RED
import org.lain.engine.client.render.MENTION
import org.lain.engine.client.render.Rect2
import org.lain.engine.util.Color

data class ChatChannelButton(val textWidth: Int, val rect: Rect2, val text: Text, val section: ChatBarSection)

data class ChatChannelsBar(var buttons: List<ChatChannelButton> = listOf()) {
    private val client by injectClient()
    private val padding = 2f
    private val buttonSpacing = 2f
    private val iconReserve = 10f

    private val chatManager: ClientEngineChatManager? get() = client.gameSession?.chatManager
    private var sections = listOf<ChatBarSection>()

    private val chatHud
        get() = MinecraftClient.inGameHud.chatHud

    val height get() = MinecraftClient.textRenderer.fontHeight + (padding * 2)
    var width = 0f
        private set

    fun updateButtons(sections: List<ChatBarSection>) {
        this.sections = sections
        measure()
    }

    fun measure() {
        val textRenderer = MinecraftClient.textRenderer
        var cursorX = 0f
        val result = mutableListOf<ChatChannelButton>()

        sections.forEach { section ->
            val text = Text.of(section.name)
            val textWidth = textRenderer.getWidth(text)

            val buttonWidth = textWidth + padding * 2 + iconReserve

            val rect = Rect2(
                cursorX,
                0f,
                cursorX + buttonWidth,
                height
            )

            result += ChatChannelButton(
                textWidth,
                rect,
                text,
                section
            )

            cursorX += buttonWidth + buttonSpacing
        }

        buttons = result
        width = if (cursorX > 0f) cursorX - buttonSpacing else 0f
    }

    fun onClick(mouseX: Float, mouseY: Float) {
        buttons.forEach {
            val rect = it.rect
            if (mouseX in rect.x1..rect.x2 && mouseY in rect.y1..rect.y2) {
                chatManager?.chatBar?.toggleHide(it.section, chatManager)
                client.audioManager.playUiNotificationSound()
            }
        }
    }

    fun renderChatChannelsBar(context: DrawContext, mouseX: Float, mouseY: Float) {
        val client = MinecraftClient
        val chatBar = chatManager?.chatBar ?: return

        val textRenderer = client.textRenderer
        val background = client.options.getTextBackgroundColor(Int.MIN_VALUE)

        buttons
            .forEach { button ->
                val rect = button.rect
                val x1 = rect.x1.toInt()
                val y1 = rect.y1.toInt()
                val x2 = rect.x2.toInt()
                val y2 = rect.y2.toInt()

                var color = if (mouseX in rect.x1..rect.x2 && mouseY in rect.y1..rect.y2) {
                    Color.WHITE
                } else {
                    Color.GRAY
                }
                if (chatManager?.chatBar?.isHidden(button.section) == true) {
                    color = color.blend(Color.DARK_RED, 0.5f)
                }

                val textWidth = button.textWidth
                val rectWidth = x2 - x1

                context.fill(x1, y1, x2, y2, background)
                context.drawTextWithShadow(
                    textRenderer,
                    button.text,
                    x1 + padding.toInt(),
                    y1 + padding.toInt(),
                    color.integer
                )

                if (chatBar.hasUnreadMessages(button.section)) {
                    val iconSize = 8f

                    val mentioned = chatBar.wasMentioned(button.section)
                    val sprite = if (mentioned) MENTION else EXCLAMATION_RED

                    context.drawEngineSprite(
                        sprite,
                        x1 + rectWidth - iconSize - 2f,
                        y1 + 1f,
                        iconSize,
                        iconSize
                    )
                }
            }
    }
}