package org.lain.engine.client.render.world

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence
import org.apache.logging.log4j.core.pattern.TextRenderer
import org.lain.engine.client.mc.ImmediateVertexConsumers
import org.lain.engine.mc.EntityTable
import org.lain.engine.util.Color
import org.lain.engine.util.math.EVec3

data class ImmediateWorldRenderContext(
    val entityTable: EntityTable,
    val vertexConsumers: ImmediateVertexConsumers,
    val textRenderer: Font,
    val matrices: PoseStack,
)

data class LabelRenderState(
    val labelPos: EVec3,
    val labelAlpha: Float,
    val labelLines: List<Line>,
    val scale: Float
) {
    data class Line(val text: FormattedCharSequence, val width: Int)
}

fun Font.labelRenderStateLine(text: FormattedCharSequence): LabelRenderState.Line {
    return LabelRenderState.Line(text, width(text))
}

data class LabelEasing(val squaredDistanceToCamera: Float, val squaredDistance: Float)

context(ctx: ImmediateWorldRenderContext)
fun renderLabel(
    camera: Camera,
    renderState: LabelRenderState,
    backgroundOpacity: Float,
    light: Int,
    easing: LabelEasing? = null,
) {
    val cameraX = camera.position().x
    val cameraY = camera.position().y
    val cameraZ = camera.position().z
    val (labelPos, labelAlpha, labelLines) = renderState
    if (labelAlpha <= 0f) return

    ctx.matrices.pushPose()
    ctx.matrices.translate(
        (labelPos.x - cameraX),
        (labelPos.y - cameraY) + 0.07f,
        (labelPos.z - cameraZ)
    )
    ctx.matrices.mulPose(camera.rotation())
    ctx.matrices.scale(renderState.scale, -renderState.scale, renderState.scale)

    var alpha = labelAlpha
    if (easing != null) {
        val startFade = easing.squaredDistance * 0.7f
        val endFade = easing.squaredDistance
        val current = easing.squaredDistanceToCamera

        if (current > startFade) {
            val t = (current - startFade) / (endFade - startFade)
            alpha *= (1f - t).coerceIn(0f, 1f)
        }
    }

    var y = 0f
    for (line in labelLines) {
        val offset = -(line.width / 2.0f)
        val textColor = Color.WHITE.withAlpha((alpha * 255).toInt())
        val backgroundColor = Color.BLACK.withAlpha((backgroundOpacity * alpha * 255f).toInt())
        y -= ctx.textRenderer.lineHeight

        val matrix = ctx.matrices.last().pose()
        ctx.textRenderer.drawInBatch(
            line.text,
            offset,
            y,
            textColor.integer,
            false,
            matrix,
            ctx.vertexConsumers,
            Font.DisplayMode.SEE_THROUGH,
            backgroundColor.integer,
            light
        )
    }

    ctx.matrices.popPose()
}