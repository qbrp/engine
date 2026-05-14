package org.lain.engine.client.mc.render.world

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.util.CommonColors
import org.lain.engine.client.mc.render.ColorMc
import org.lain.engine.mc.Text

import kotlin.math.abs
import kotlin.math.max

fun renderAcousticDebugLabels(
    volumes: List<Pair<BlockPos, Float>>,
    hide: List<BlockPos>,
    baseVolume: Float,
    maxVolume: Float,
    queue: OrderedSubmitNodeCollector,
    matrices: PoseStack,
    cameraRenderState: CameraRenderState
) {
    for ((pos, volume) in volumes) {
        if (pos in hide) continue
        val camPos = cameraRenderState.pos
        val centerPos = pos.center
        val renderPos = centerPos
            .subtract(camPos)
            .add(0.0, -0.5, 0.0)
        val distance = cameraRenderState.pos.distanceToSqr(centerPos)

        val t = ((volume - baseVolume) / (maxVolume - baseVolume))
            .coerceIn(-1f, 1f)

        val red = (255f * max(0f,  t)).toInt()
        val blue = (255f * max(0f, -t)).toInt()
        val green = (255f * (1f - abs(t))).toInt()

        queue.submitNameTag(
            matrices,
            renderPos,
            0,
            Text.literal("%.2f".format(volume))
                .withColor(
                    if (volume > 0.05f) {
                        ColorMc.color(red, green, blue)
                    } else {
                        CommonColors.GRAY
                    }
                ),
            true,
            LightTexture.FULL_BRIGHT,
            distance,
            cameraRenderState
        )
    }
}