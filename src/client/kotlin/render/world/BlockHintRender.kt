package org.lain.engine.client.render.world

import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import org.lain.engine.client.util.ClientHintState
import org.lain.engine.client.control.InspectionMode
import org.lain.engine.mc.engine
import org.lain.engine.mc.literalText
import org.lain.engine.world.Hint
import org.lain.engine.world.ImmutableVoxelPos
import kotlin.math.pow
import kotlin.math.truncate

private val NOT_READ = literalText("?").withStyle(ChatFormatting.YELLOW).visualOrderText
private val CHANGED = literalText("?").withStyle(ChatFormatting.GOLD).visualOrderText
private val READ = literalText("?").withStyle(ChatFormatting.DARK_GRAY).visualOrderText

data class BlockHintInspectionRenderState(var opacity: Float = 0f)

context(ctx: ImmediateWorldRenderContext)
fun renderBlockHints(
    camera: Camera,
    hintState: ClientHintState,
    inspectionMode: InspectionMode,
    inspection: Boolean,
    inspectionTextWidth: Int,
    hints: Map<ImmutableVoxelPos, Hint>,
    textCache: TextCache,
    dt: Float,
) {
    val hint = inspectionMode.inspectHint
    val screenRenderer = ctx.screenRenderer

    val easingDistance = when(inspection) {
        true -> 12f
        false -> 4f
    }
    val easingDistanceSqr = easingDistance * easingDistance

    val backgroundOpacity = when(inspection) {
        true -> 0.35f
        false -> 0f
    }

    val fade = 0.5f
    val renderState = screenRenderer.blockHintInspectionRenderState ?: BlockHintInspectionRenderState().also {
        screenRenderer.blockHintInspectionRenderState = it
    }
    val targetInspectionOpacity = if (inspection) 1f else 0f
    val opacityStep = 1f - 0.7f.pow(dt.coerceAtLeast(0f))
    renderState.opacity += (targetInspectionOpacity - renderState.opacity) * opacityStep
    renderState.opacity = renderState.opacity.coerceIn(0f, 1f)

    if (inspection && hint != null) {
        val size = hint.hint.texts.size
        val (str, index) = hint.computeText()
        val text = if (size <= 1) {
            str
        } else {
            "<gold>[$index / ${size - 1}]</gold><newline>$str"
        }
        val centerPos = inspectionMode.voxelPos.toCenterPos()

        val scale = 0.0145f
        val lines = ctx.textRenderer.split(textCache.get(text), inspectionTextWidth)
            .map { LabelRenderState.Line(it, ctx.textRenderer.width(it)) }
            .reversed()
        val pos = centerPos.sub(y = (lines.size * ctx.textRenderer.lineHeight * scale) - 0.5f)

        renderLabel(
            camera,
            LabelRenderState(pos, renderState.opacity, lines, scale),
            0.3f,
            LightTexture.FULL_BRIGHT
        )
    }

    hints.forEach { (pos, hint) ->
        val text = NOT_READ
        val centerPos = pos.toCenterPos()
        val baseMultiplierAlpha = when (inspection) {
            true -> 0.3f
            false -> 0.1f
        }
        val multiplierAlpha = baseMultiplierAlpha + 0.7f * renderState.opacity

        if (!inspection || inspectionMode.voxelPos != pos) {
            renderLabel(
                camera,
                LabelRenderState(
                    centerPos,
                    multiplierAlpha,
                    listOf(ctx.textRenderer.labelRenderStateLine(text)),
                    0.0285f
                ),
                backgroundOpacity,
                LightTexture.FULL_BRIGHT,
                easing = LabelEasing(
                    centerPos.squaredDistanceTo(camera.position.engine()),
                    easingDistanceSqr,
                    fade
                )
            )
        }
    }
}
