package org.lain.engine.client.mc.render

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.EditBoxWidget
import net.minecraft.client.gui.widget.ScrollableWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ColorHelper
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.mc.engine
import org.lain.engine.util.Color
import org.lain.engine.util.math.easeOutQuadratic
import org.lain.engine.world.BlockHint
import org.lwjgl.glfw.GLFW

class BlockHintList(val blockHint: BlockHint, x: Int, y: Int, width: Int, height: Int) : ScrollableWidget(x, y, width, height, Text.of("Block Hint")) {
    private val textRenderer = MinecraftClient.textRenderer
    private val notes = blockHint.texts.map {
        val text = it.parseMiniMessageClient()
        val lines = textRenderer.wrapLines(text, width)
        val height = textRenderer.fontHeight * lines.count()
        Note(lines, height)
    }

    class Note(val lines: List<OrderedText>, val height: Int)

    override fun getContentsHeightWithPadding(): Int {
        return notes.sumOf { it.height } + notes.count() * 2
    }

    override fun getDeltaYPerScroll(): Double {
        return 10.0
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float
    ) {
        var yOffset = 0
        notes.forEachIndexed { index, note ->
            context.fill(
                x,
                y + yOffset,
                x + width,
                y + yOffset + note.height,
                ColorHelper.withAlpha(200, Colors.BLACK)
            )
            var textY = 0
            note.lines.forEach {
                context.drawText(textRenderer, it, x, y + yOffset + textY, Color.ORANGE.integer, true)
                textY += textRenderer.fontHeight
            }
            yOffset += note.height + 2
        }
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {}
}

class BlockHintEditorScreen(
    private val engineClient: EngineClient,
    val blockHint: BlockHint,
    val blockPos: BlockPos
) : Screen(Text.of("Block Hint Editor")) {
    private var time = 0f
    private val boxWidth = 190
    private val boxHeight = 70
    private lateinit var editBox: EditBoxWidget
    private lateinit var blockHintList: BlockHintList

    override fun init() {
        editBox = addDrawableChild(
            EditBoxWidget.builder()
                .hasOverlay(false)
                .textColor(Color.ORANGE.integer)
                .cursorColor(Color.ORANGE.integer)
                .hasBackground(false)
                .textShadow(false)
                .placeholder(Text.literal("Введите текст...").withColor(Colors.DARK_GRAY))
                .x(width - boxWidth - 2).y(height - boxHeight - 2)
                .build(this.textRenderer, boxWidth, boxHeight, ScreenTexts.EMPTY)
        )
        editBox.setMaxLength(1024)
        editBox.setMaxLines((boxHeight / this.textRenderer.fontHeight) - 1)

        blockHintList = addDrawableChild(
            BlockHintList(blockHint, width - 190 - 2, height - boxHeight - 2, boxWidth, boxHeight)
        )
    }

    override fun setInitialFocus() { super.setInitialFocus(editBox) }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        time += deltaTicks
        val progress = easeOutQuadratic(time / 15f).coerceIn(0f, 1f)

        context.matrices.pushMatrix()
        context.matrices.translate((1 - progress) * editBox.width, 0f)
        context.fill(width - boxWidth - 2, height - boxHeight - 2, width, height, ColorHelper.withAlpha(200, Colors.BLACK))
        editBox.render(context, mouseX, mouseY, deltaTicks)
        context.matrices.popMatrix()

        val textRenderer = MinecraftClient.textRenderer
        context.drawText(
            textRenderer,
            "Чтобы создать описание блока, нажмите CTRL и Enter",
            2, height - 2 - textRenderer.fontHeight,
            Color.ORANGE.integer,
            true
        )
        blockHintList.render(context, mouseX, mouseY, deltaTicks)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (!editBox.isFocused || input.hasCtrl()) {
            if (input.key == GLFW.GLFW_KEY_ENTER && editBox.text.trim().isNotEmpty()) {
                engineClient.handler.onBlockHintAdd(blockPos.engine(), editBox.text)
                engineClient.audioManager.playUiNotificationSound()
                val newBlockHint = blockHintList.blockHint.withText(editBox.text)
                remove(blockHintList)
                blockHintList = addDrawableChild(
                    BlockHintList(newBlockHint, width - 190 - 2, 2, boxWidth, 150)
                )
                editBox.text = ""
                editBox.isFocused = false
                return true
            }
        }
        return super.keyPressed(input)
    }
}