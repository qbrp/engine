package org.lain.engine.client.render.world

import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.util.CommonColors
import org.lain.engine.client.render.ui.ColorMc
import org.lain.engine.mc.Text
import org.lain.engine.mc.engine
import kotlin.math.abs
import kotlin.math.max

context(ctx: ImmediateWorldRenderContext)
fun renderAcousticDebugLabels(
    volumes: List<Pair<BlockPos, Float>>,
    hide: List<BlockPos>,
    baseVolume: Float,
    maxVolume: Float,
    camera: Camera
) {
    for ((pos, volume) in volumes) {
        if (pos in hide) continue
        val center = pos.center
        val t = ((volume - baseVolume) / (maxVolume - baseVolume)).coerceIn(-1f, 1f)
        val red = (255f * max(0f, t)).toInt()
        val blue = (255f * max(0f, -t)).toInt()
        val green = (255f * (1f - abs(t))).toInt()
        val text = Text.literal("%.2f".format(volume))
            .withColor(
                if (volume > 0.05f) {
                    ColorMc.color(red, green, blue)
                } else {
                    CommonColors.GRAY
                }
            )
            .visualOrderText

        renderLabel(
            camera,
            LabelRenderState(
                center.engine().sub(y = 0.5f),
                1f,
                listOf(ctx.textRenderer.labelRenderStateLine(text)),
                0.025f
            ),
            0f,
            LightTexture.FULL_BRIGHT
        )
    }
}
