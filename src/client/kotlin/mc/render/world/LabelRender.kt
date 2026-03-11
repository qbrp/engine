package org.lain.engine.client.mc.render.world

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.OrderedText
import org.lain.engine.client.mc.render.TextCache
import org.lain.engine.mc.EntityTable
import org.lain.engine.util.Color
import org.lain.engine.util.math.Vec3

private val LABEL_TEXT_CACHE = TextCache()

data class ImmediateWorldRenderContext(
    val entityTable: EntityTable,
    val vertexConsumers: VertexConsumerProvider.Immediate,
    val textRenderer: TextRenderer,
    val matrices: MatrixStack,
)

data class LabelRenderState(
    val labelPos: Vec3,
    val labelAlpha: Float,
    val labelLines: List<Line>,
    val scale: Float
) {
    data class Line(val text: OrderedText, val width: Int)
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
    val cameraX = camera.pos.x
    val cameraY = camera.pos.y
    val cameraZ = camera.pos.z
    val (labelPos, labelAlpha, labelLines) = renderState
    if (labelAlpha <= 0f) return

    ctx.matrices.push()
    ctx.matrices.translate(
        (labelPos.x - cameraX),
        (labelPos.y - cameraY) + 0.07f,
        (labelPos.z - cameraZ)
    )
    ctx.matrices.multiply(camera.rotation)
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
        y -= ctx.textRenderer.fontHeight

        val matrix = ctx.matrices.peek().positionMatrix
        ctx.textRenderer.draw(
            line.text,
            offset,
            y,
            textColor.integer,
            false,
            matrix,
            ctx.vertexConsumers,
            TextRenderer.TextLayerType.SEE_THROUGH,
            backgroundColor.integer,
            light
        )
    }

    ctx.matrices.pop()
}