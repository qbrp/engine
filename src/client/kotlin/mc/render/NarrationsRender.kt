package org.lain.engine.client.mc.render

import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Colors
import net.minecraft.util.math.ColorHelper
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.player.NarrationMessage
import org.lain.engine.util.math.randomFloat

data class NarrationMessageRenderState(val index: Int, var time: Float)

// В тиках
private const val NARRATION_FADE_TIME = 10f

fun renderNarrations(
    context: DrawContext,
    narrations: List<NarrationMessageRenderState>,
    messages: List<NarrationMessage>,
    dt: Float
) {
    val textRenderer = MinecraftClient.textRenderer
    var y = 20
    for (renderState in narrations) {
        val narration = messages[renderState.index]
        val content = narration.content
        val text = content.text.parseMiniMessageClient()
        val duration = content.duration
        val textWidth = textRenderer.getWidth(text)
        val time = renderState.time

        val t1 = (time / NARRATION_FADE_TIME).coerceIn(0f, 1f)
        val t2 = ((time - duration) / NARRATION_FADE_TIME).coerceIn(0f, 1f)
        val t = t1 - t2

        val tShake = t1 - 1f
        val shakeX = ((randomFloat() * tShake * 2) - tShake) * 3f
        val shakeY = ((randomFloat() * tShake * 2) - tShake) * 3f

        context.drawTextWithShadow(
            textRenderer,
            text,
            context.scaledWindowWidth / 2 - textWidth / 2 + shakeX.toInt(),
            y + shakeY.toInt(),
            ColorHelper.withAlpha(t, Colors.WHITE),
        )
        y += textRenderer.fontHeight + 1
        renderState.time += dt
    }
}