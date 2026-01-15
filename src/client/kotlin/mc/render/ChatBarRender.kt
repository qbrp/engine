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
import org.lain.engine.client.render.Size
import org.lain.engine.client.render.fitSquaresHorizontally
import org.lain.engine.util.Color

data class ChatChannelButton(val textWidth: Int, val rect: Rect2, val text: Text, val section: ChatBarSection)

data class ChatChannelsBar(var buttons: List<ChatChannelButton> = listOf()) {
    private val client by injectClient()
    private val padding = 1f
    private val chatManager: ClientEngineChatManager? get() = client.gameSession?.chatManager
    private var sections = listOf<ChatBarSection>()
    private val chatHud
        get() = MinecraftClient.inGameHud.chatHud
    val height get() = MinecraftClient.textRenderer.fontHeight + (padding * 2)
    val width get() = 320f + 4 + 4 + LINE_INDENT

    fun updateButtons(sections: List<ChatBarSection>) {
        this.sections = sections
        measure()
    }

    fun measure() {
        val textRenderer = MinecraftClient.textRenderer
        val fontHeight = textRenderer.fontHeight

        val sizes = mutableMapOf<ChatBarSection, Int>()
        val texts = mutableMapOf<ChatBarSection, Text>()

        buttons = sections.fitSquaresHorizontally(width, height) {
            val text = Text.of(it.name)
            val textWidth = textRenderer.getWidth(text)
            sizes[it] = textWidth
            texts[it] = text
            Size(
                textWidth.toFloat(),
                fontHeight.toFloat()
            )
        }.map { (section, rect2) ->
            ChatChannelButton(
                sizes[section]!!,
                rect2,
                texts[section]!!,
                section
            )
        }
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
                    x1 + (rectWidth - textWidth) / 2,
                    y1 + padding.toInt(),
                    color.integer
                )

                if (chatBar.hasUnreadMessages(button.section)) {
                    val iconSize = 8f

                    val mentioned = chatBar.wasMentioned(button.section)
                    val sprite = if (mentioned) MENTION else EXCLAMATION_RED

                    context.drawEngineSprite(
                        sprite,
                        x1 + rectWidth - iconSize,
                        y1 - iconSize / 2f,
                        iconSize,
                        iconSize
                    )
                }
            }
    }
}