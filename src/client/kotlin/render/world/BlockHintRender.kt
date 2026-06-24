package org.lain.engine.client.render.world

import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import org.lain.engine.client.ClientHintState
import org.lain.engine.client.HintState
import org.lain.engine.client.chat.ChatBubble
import org.lain.engine.client.render.legacy.TextCache
import org.lain.engine.mc.engine
import org.lain.engine.mc.literalText
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.Hint
import org.lain.engine.world.VoxelPos

private val NOT_READ = literalText("?").withStyle(ChatFormatting.YELLOW).visualOrderText
private val CHANGED = literalText("?").withStyle(ChatFormatting.GOLD).visualOrderText
private val READ = literalText("?").withStyle(ChatFormatting.DARK_GRAY).visualOrderText

context(ctx: ImmediateWorldRenderContext)
fun renderBlockHints(
    camera: Camera,
    hintState: ClientHintState,
    hints: Map<VoxelPos, Hint>,
    dt: Float,
) = hints.forEach { (pos, hint) ->
    val state = hintState.stateOf(hint.uuid)
    val text = when(state) {
        HintState.NOT_READ -> NOT_READ
        HintState.CHANGED -> CHANGED
        HintState.READ -> READ
    }
    val centerPos = pos.toCenterPos()
    val easingDistance = 12.0f*12.0f
    renderLabel(
        camera,
        LabelRenderState(
            centerPos,
            0.5f,
            listOf(ctx.textRenderer.labelRenderStateLine(text)),
            0.0285f
        ),
        0.25f,
        LightTexture.FULL_BRIGHT,
        easing = LabelEasing(
            centerPos.squaredDistanceTo(camera.position().engine()),
            easingDistance,
            fade = 0.5f
        )
    )
}