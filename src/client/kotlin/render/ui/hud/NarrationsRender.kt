package org.lain.engine.client.render.ui.hud

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.CommonColors
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.client.render.ui.ColorMc
import org.lain.engine.player.NarrationMessage
import org.lain.engine.util.math.lerp
import org.lain.engine.util.math.randomFloat
import kotlin.math.pow

data class NarrationMessageRenderState(
    val message: NarrationMessage,
    var opacity: Float = 0f,
    var time: Float = 0f,
)

// В тиках
private const val NARRATION_FADE_TIME = 10f

fun renderNarrations(
    context: GuiGraphics,
    narrations: List<NarrationMessageRenderState>,
    dt: Float
) {
    val textRenderer = MinecraftClient.font
    var y = 20
    for (renderState in narrations) {
        val narration = renderState.message
        val text = narration.text.parseMiniMessageClient()
        val duration = narration.duration
        val textWidth = textRenderer.width(text)
        val time = renderState.time

        val t1 = (time / NARRATION_FADE_TIME).coerceIn(0f, 1f)
        val t2 = ((time - duration + NARRATION_FADE_TIME) / NARRATION_FADE_TIME).coerceIn(0f, 1f)
        val t = t1 - t2

        val tShake = t1 - 1f
        val shakeX = ((randomFloat() * tShake * 2) - tShake) * 3f
        val shakeY = ((randomFloat() * tShake * 2) - tShake) * 3f

        val color = ColorMc.color(renderState.opacity, CommonColors.WHITE)
        if (ColorMc.alpha(color) >= 4) {
            context.drawString(
                textRenderer,
                text,
                context.guiWidth() / 2 - textWidth / 2 + shakeX.toInt(),
                y + shakeY.toInt(),
                color,
            )
        }
        y += textRenderer.lineHeight + 1
        renderState.opacity = lerp(renderState.opacity, t, 1f - 0.3f.pow(dt))
        renderState.time += dt
    }
}
