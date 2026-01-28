package org.lain.engine.client.render.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import org.lain.engine.client.MinecraftEngineClient
import org.lain.engine.client.mc.render.fill
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.Window
import org.lain.engine.util.BLACK_TRANSPARENT_BG_COLOR
import org.lain.engine.util.Color
import org.lain.engine.util.text.EngineText
import org.lain.engine.util.text.TextColor
import org.lain.engine.util.text.toMinecraft

interface Ui {
    var gap: Float

    fun begin(x: Float, y: Float, width: Float? = null, height: Float? = null, background: Color = BLACK_TRANSPARENT_BG_COLOR)
    fun drawRect(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, color2: Color = color)
    fun moveCursor(x: Float, y: Float)
    fun text(text: EngineText)
    fun text(literal: String, color: Color = Color.WHITE)
    fun end()
}

class MinecraftImmediateUiDrawer(
    private val drawContext: DrawContext,
    private val fontRenderer: FontRenderer,
    private val client: MinecraftClient
) : Ui {
    private data class Window(
        val x: Float,
        val y: Float,
        var width: Float?,
        var height: Float?,
        var cursorBasicX: Float = 0f,
        var cursorBasicY: Float = 1f,
        var bg: Color
    )
    private val windowsStack = ArrayDeque<Window>()
    override var gap: Float = 2f

    override fun begin(x: Float, y: Float, width: Float?, height: Float?, background: Color) {
        windowsStack.addLast(Window(x, y, width, height, bg = background))
        drawContext.matrices.pushMatrix()
        drawContext.matrices.translate(x, y)
    }

    override fun drawRect(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, color2: Color) {
        drawContext.fill(x1, y1, x2, y2, color.integer)
        moveCursor(x2 - x1, y2 - y1)
    }

    override fun moveCursor(x: Float, y: Float) {
        drawContext.matrices.translate(
            peek().cursorBasicX * (x + gap),
            peek().cursorBasicY * (y + gap)
        )
    }

    override fun text(text: EngineText) {
        val text = fontRenderer.breakTextByLines(text, peek().width!!)
        var y = 0
        text.forEach { line ->
            drawContext.drawTextWithShadow(
                client.textRenderer,
                line.toMinecraft(),
                0,
                y,
                Color.WHITE.integer
            )
            y += client.textRenderer.fontHeight
        }
    }

    override fun text(literal: String, color: Color) {
        text(EngineText(literal, TextColor.Single(color)))
    }

    override fun end() {
        windowsStack.removeLast()
        drawContext.matrices.popMatrix()
    }

    private fun peek() = windowsStack.last()
}