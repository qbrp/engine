package org.lain.engine.client.render.world

import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import org.lain.engine.client.ClientHintState
import org.lain.engine.client.HintState
import org.lain.engine.client.chat.ChatBubble
import org.lain.engine.client.control.InspectionMode
import org.lain.engine.client.render.legacy.TextCache
import org.lain.engine.mc.engine
import org.lain.engine.mc.literalText
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.Hint
import org.lain.engine.world.VoxelPos

private val NOT_READ = literalText("?").withStyle(ChatFormatting.YELLOW).visualOrderText
private val CHANGED = literalText("?").withStyle(ChatFormatting.GOLD).visualOrderText
private val READ = literalText("?").withStyle(ChatFormatting.DARK_GRAY).visualOrderText

data class BlockHintInspectionRenderState(var time: Float = 0f)

context(ctx: ImmediateWorldRenderContext)
fun renderBlockHints(
    camera: Camera,
    hintState: ClientHintState,
    inspectionMode: InspectionMode,
    inspection: Boolean,
    inspectionTextWidth: Int,
    hints: Map<VoxelPos, Hint>,
    textCache: TextCache,
    dt: Float,
) {
    val hint = inspectionMode.inspectHint
    val screenRenderer = ctx.screenRenderer

    val easingDistance = 12f * 12f
    val fade = 0.5f

    if (inspection && hint != null) {
        val renderState = screenRenderer.blockHintInspectionRenderState ?: run {
            val state = BlockHintInspectionRenderState()
            screenRenderer.blockHintInspectionRenderState
            state
        }

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
            LabelRenderState(pos, 0.5f, lines, scale),
            0.25f,
            LightTexture.FULL_BRIGHT
        )

        renderState.time += dt
    } else {
        screenRenderer.blockHintInspectionRenderState = null
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
        val multiplierAlpha = if (!inspection) 0.3f else 1f

        if (!inspection || inspectionMode.voxelPos != pos) {
            renderLabel(
                camera,
                LabelRenderState(
                    centerPos,
                    0.5f * multiplierAlpha,
                    listOf(ctx.textRenderer.labelRenderStateLine(text)),
                    0.0285f
                ),
                0.25f * multiplierAlpha,
                LightTexture.FULL_BRIGHT,
                easing = LabelEasing(
                    centerPos.squaredDistanceTo(camera.position().engine()),
                    easingDistance,
                    fade
                )
            )
        }
    }
}