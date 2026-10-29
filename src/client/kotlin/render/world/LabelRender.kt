package org.lain.engine.client.render.world

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence
import org.lain.engine.client.render.ScreenRenderer
import org.lain.engine.util.Color
import org.lain.engine.util.math.EVec3
import kotlin.math.roundToInt

private const val MIN_VISIBLE_TEXT_ALPHA = 4
data class ImmediateWorldRenderContext(
    val labels: WorldLabelRenderPass,
    val textRenderer: Font,
    val matrices: PoseStack,
    val screenRenderer: ScreenRenderer
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

data class LabelEasing(val squaredDistanceToCamera: Float, val squaredDistance: Float, val fade: Float = 0.7f)

context(ctx: ImmediateWorldRenderContext)
fun renderLabel(
    camera: Camera,
    renderState: LabelRenderState,
    backgroundOpacity: Float,
    light: Int,
    easing: LabelEasing? = null,
) {
    val cameraX = camera.position.x
    val cameraY = camera.position.y
    val cameraZ = camera.position.z
    val (labelPos, labelAlpha, labelLines) = renderState
    if (labelLines.isEmpty()) return
    var alpha = labelAlpha.coerceIn(0f, 1f)
    if (easing != null) {
        val endFade = easing.squaredDistance.coerceAtLeast(0f)
        val startFade = endFade * easing.fade.coerceIn(0f, 1f)
        val current = easing.squaredDistanceToCamera.coerceAtLeast(0f)

        if (current > startFade) {
            val fadeDistance = endFade - startFade
            val t = if (fadeDistance > 0f) {
                (current - startFade) / fadeDistance
            } else {
                1f
            }
            alpha *= (1f - t).coerceIn(0f, 1f)
        }
    }

    val textAlpha = (alpha * 255f).roundToInt()
    // Font treats alpha 0..3 as legacy RGB and makes it opaque.
    if (textAlpha < MIN_VISIBLE_TEXT_ALPHA) return

    val textColor = Color.WHITE.withAlpha(textAlpha)
    val backgroundAlpha = (backgroundOpacity.coerceIn(0f, 1f) * alpha * 255f).roundToInt()
    val backgroundColor = Color.BLACK.withAlpha(backgroundAlpha)

    ctx.matrices.pushPose()
    try {
        ctx.matrices.translate(
            (labelPos.x - cameraX),
            (labelPos.y - cameraY) + 0.07f,
            (labelPos.z - cameraZ)
        )
        ctx.matrices.mulPose(camera.rotation())
        ctx.matrices.scale(renderState.scale, -renderState.scale, renderState.scale)
        ctx.labels.submit(
            ctx.matrices.last().pose(),
            labelLines,
            textColor.integer,
            backgroundColor.integer,
            light,
        )
    } finally {
        ctx.matrices.popPose()
    }
}
