package org.lain.engine.client.render.world

import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import org.lain.engine.client.ClientHintState
import org.lain.engine.client.control.InspectionMode
import org.lain.engine.client.render.legacy.TextCache
import org.lain.engine.mc.ecs.engine
import org.lain.engine.mc.engine
import org.lain.engine.mc.literalText
import org.lain.engine.world.Hint
import org.lain.engine.world.ImmutableVoxelPos
import org.lain.engine.world.VoxelPos
import kotlin.math.pow

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

    val easingDistance = 12f * 12f
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
            "<gray>$str"
        } else {
            "<gold>[$index / ${size - 1}]</gold><newline><gray>$str"
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
            0.25f,
            LightTexture.FULL_BRIGHT
        )
    }

    hints.forEach { (pos, hint) ->
//        val state = hintState.stateOf(hint.uuid)
//        val text = when (state) {
//            HintState.NOT_READ -> NOT_READ
//            HintState.CHANGED -> CHANGED
//            HintState.READ -> READ
//        }
        // TODO
        val text = NOT_READ
        val centerPos = pos.toCenterPos()
        val multiplierAlpha = 0.3f + 0.7f * renderState.opacity

        if (!inspection || inspectionMode.voxelPos != pos) {
            renderLabel(
                camera,
                LabelRenderState(
                    centerPos,
                    multiplierAlpha,
                    listOf(ctx.textRenderer.labelRenderStateLine(text)),
                    0.0285f
                ),
                0.25f,
                LightTexture.FULL_BRIGHT,
                easing = LabelEasing(
                    centerPos.squaredDistanceTo(camera.position.engine()),
                    easingDistance,
                    fade
                )
            )
        }
    }
}
