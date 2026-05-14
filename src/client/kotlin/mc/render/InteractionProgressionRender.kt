package org.lain.engine.client.mc.render

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.CommonColors
import org.lain.engine.mc.Text
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.mc.engineId
import org.lain.engine.mc.literalText
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.ProgressionType
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
    literalText("."),
    literalText(".."),
    literalText("...")
)

private const val DOT_ANIMATION_SPEED = 5
private const val FADE_DELAY = 20

fun renderInteractionProgression(
    context: GuiGraphics,
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
        renderState.texture = if (frames.isEmpty()) null else engineId(frames[index])
        renderState.opacity = lerp(renderState.opacity, 1f, 1f - 0.7f.pow(dt))
        var text: String
        if (progress < 1f) {
            text = animation.progressionText
        } else {
            renderState.endTime = renderState.time
            text = animation.successText
        }
        interaction.text?.let { text = it }
        interaction.placeholders.forEach { (placeholder, set) ->
            text = text.replace("{$placeholder}", set)
        }
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
                context.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    2 + offset,
                    context.guiHeight() - 10 - scale + offset,
                    scale,
                    scale,
                    ColorMc.color(renderState.opacity, color)
                )
            }

            draw(CommonColors.BLACK, 1)
            draw(CommonColors.WHITE)
        }

        val textRenderer = MinecraftClient.font
        renderState.text?.let { text ->
            val y = context.guiHeight() - 2 - 10 - (scale / 2)
            val color = ColorMc.color(renderState.opacity, CommonColors.WHITE)
            context.drawString(textRenderer, text, 2 + scale + 4, y, color)
            val width = textRenderer.width(text)
            if (progress < 1f && interaction != null && interaction.text == null) {
                val dotIndex = ((renderState.time / DOT_ANIMATION_SPEED).toInt()) % DOTS.size
                val dot = DOTS[dotIndex]
                context.drawString(
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