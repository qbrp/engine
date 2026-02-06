package org.lain.engine.client.mc.render

import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.command.RenderCommandQueue
import net.minecraft.client.render.entity.EntityRenderManager
import net.minecraft.client.render.state.CameraRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ColorHelper
import org.lain.engine.world.Direction
import kotlin.math.abs
import kotlin.math.max

fun renderAcousticDebugLabels(
    volumes: List<Pair<BlockPos, Float>>,
    hide: List<BlockPos>,
    baseVolume: Float,
    maxVolume: Float,
    queue: RenderCommandQueue,
    matrices: MatrixStack,
    cameraRenderState: CameraRenderState
) {
    for ((pos, volume) in volumes) {
        if (pos in hide) continue
        val camPos = cameraRenderState.pos
        val centerPos = pos.toCenterPos()
        val renderPos = centerPos
            .subtract(camPos)
            .add(0.0, -0.5, 0.0)
        val distance = cameraRenderState.pos.squaredDistanceTo(centerPos)

        val t = ((volume - baseVolume) / (maxVolume - baseVolume))
            .coerceIn(-1f, 1f)

        val red = (255f * max(0f,  t)).toInt()
        val blue = (255f * max(0f, -t)).toInt()
        val green = (255f * (1f - abs(t))).toInt()

        queue.submitLabel(
            matrices,
            renderPos,
            0,
            Text.literal("%.2f".format(volume))
                .withColor(
                    if (volume > 0.05f) {
                        ColorHelper.getArgb(red, green, blue)
                    } else {
                        Colors.GRAY
                    }
                ),
            true,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            distance,
            cameraRenderState
        )
    }
}