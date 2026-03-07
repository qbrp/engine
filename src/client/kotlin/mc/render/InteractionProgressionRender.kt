package org.lain.engine.client.mc.render

import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.Identifier
import net.minecraft.util.math.ColorHelper
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.ProgressionType
import org.lain.engine.util.EngineId
import org.lain.engine.util.math.lerp
import kotlin.math.pow

data class InteractionProgressionRenderState(
    var opacity: Float,
    var progressionType: ProgressionType? = null,
    var texture: Identifier? = null,
    var text: Text? = null,
    var time: Float = 0f,
    var endTime: Float = 0f,
)

private val DOTS = listOf(
    Text.of("."),
    Text.of(".."),
    Text.of("...")
)

private const val DOT_ANIMATION_SPEED = 5
private const val FADE_DELAY = 20

fun renderInteractionProgression(
    context: DrawContext,
    renderState: InteractionProgressionRenderState,
    interaction: InteractionComponent?,
    dt: Float
) {
    renderState.time += dt
    val progression = interaction?.progression
    var progress = 0f
    if (progression != null) {
        renderState.progressionType = progression

        progress = interaction.progress
        val (duration, animation) = progression
        val frames = animation.frames
        val size = frames.size.coerceAtLeast(1)
        val index = (size * progress)
            .toInt()
            .coerceIn(0, size - 1)
        renderState.texture = if (frames.isEmpty()) null else EngineId(frames[index])
        renderState.opacity = lerp(renderState.opacity, 1f, 1f - 0.7f.pow(dt))
        var text: String
        if (progress < 1f) {
            text = animation.progressionText
        } else {
            renderState.endTime = renderState.time
            text = animation.successText
        }
        interaction.text?.let { text = it }
        renderState.text = text.parseMiniMessageClient()
    } else {
        if (renderState.time - renderState.endTime > FADE_DELAY) {
            renderState.opacity = lerp(renderState.opacity, 0f, 1f - 0.7f.pow(dt))
        }
    }

    if (renderState.opacity > 0f) {
        val scale = 16
        renderState.texture?.let { texture ->
            fun draw(color: Int, offset: Int = 0) {
                context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    2 + offset,
                    context.scaledWindowHeight - 10 - scale + offset,
                    scale,
                    scale,
                    ColorHelper.withAlpha(renderState.opacity, color)
                )
            }

            draw(Colors.BLACK, 1)
            draw(Colors.WHITE)
        }

        val textRenderer = MinecraftClient.textRenderer
        renderState.text?.let { text ->
            val y = context.scaledWindowHeight - 2 - 10 - (scale / 2)
            val color = ColorHelper.withAlpha(renderState.opacity, Colors.WHITE)
            context.drawTextWithShadow(textRenderer, text, 2 + scale + 4, y, color)
            val width = textRenderer.getWidth(text)
            if (progress < 1f && interaction != null && interaction.text == null) {
                val dotIndex = ((renderState.time / DOT_ANIMATION_SPEED).toInt()) % DOTS.size
                val dot = DOTS[dotIndex]
                context.drawTextWithShadow(
                    textRenderer,
                    dot,
                    2 + scale + 4 + width + 1,
                    y,
                    color
                )
            }
        }
    }
}