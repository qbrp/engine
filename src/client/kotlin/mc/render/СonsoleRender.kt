package org.lain.engine.client.mc.render

import net.minecraft.client.gui.DrawContext
import net.minecraft.text.OrderedText
import net.minecraft.util.Colors
import net.minecraft.util.math.ColorHelper
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.util.math.MutableVec2

data class ConsoleRenderState(
    val pos: MutableVec2,
    val width: Int,
    val lines: MutableList<OrderedText>
)

fun renderConsoleHud(context: DrawContext, renderState: ConsoleRenderState) {
    val client = MinecraftClient
    val backgroundOpacity = client.options.textBackgroundOpacity.getValue().toFloat()
    val fontHeight = client.textRenderer.fontHeight
    context.matrices.pushMatrix()
    context.matrices.translate(renderState.pos.x, renderState.pos.y)
    val height = renderState.lines.count() * fontHeight
    context.fill(0, 0, renderState.width, height, ColorHelper.withAlpha(backgroundOpacity, Colors.BLACK))
    renderState.lines.forEachIndexed { index, text ->
        val y = fontHeight * index
        context.drawText(client.textRenderer, text, y, 0, Colors.WHITE, false)
    }
    context.matrices.popMatrix()
}