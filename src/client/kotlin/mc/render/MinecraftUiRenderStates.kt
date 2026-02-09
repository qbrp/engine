package org.lain.engine.client.mc.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.gui.ScreenRect
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.texture.TextureSetup
import org.joml.Matrix3x2f
import org.lain.engine.util.math.toRadians
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private fun createBounds(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    pose: Matrix3x2f,
    scissorArea: ScreenRect?
): ScreenRect {
    val screenRect = ScreenRect(
        ceil(x0).toInt(),
        ceil(y0).toInt(),
        ceil(x1 - x0).toInt(),
        ceil(y1 - y0).toInt()
    ).transformEachVertex(pose)
    return if (scissorArea != null) scissorArea.intersection(screenRect) ?: ScreenRect(0, 0, 0, 0) else screenRect
}

data class EngineGuiQuad(
    val pipeline: RenderPipeline,
    val textureSetup: TextureSetup,
    val pose: Matrix3x2f,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val col1: Int,
    val col2: Int,
    val scissorArea: ScreenRect? = null,
    val bounds: ScreenRect = createBounds(x0, y0, x1, y1, pose, scissorArea)
) : SimpleGuiElementRenderState {
    override fun setupVertices(vertices: VertexConsumer) {
        vertices.vertex(pose, x0, y0).color(col1)
        vertices.vertex(pose, x0, y1).color(col2)
        vertices.vertex(pose, x1, y1).color(col2)
        vertices.vertex(pose, x1, y0).color(col1)
    }

    override fun pipeline(): RenderPipeline = pipeline

    override fun textureSetup(): TextureSetup = textureSetup

    override fun scissorArea(): ScreenRect? = scissorArea

    override fun bounds(): ScreenRect = bounds
}

data class EngineGuiArc(
    val pipeline: RenderPipeline,
    val textureSetup: TextureSetup,
    val x: Float,
    val y: Float,
    val pose: Matrix3x2f,
    val color: Int,
    val startAngle: Float,
    val endAngle: Float,
    val radius: Float,
    val thickness: Float,
    val segments: Int,
    val fill: Float,
    val scissorArea: ScreenRect? = null,
    val bounds: ScreenRect = createBounds(0f, 0f, radius*2, radius*2, pose, scissorArea)
) : SimpleGuiElementRenderState {
    override fun setupVertices(vertices: VertexConsumer) {
        val filledSegments = ceil(segments * fill).toInt()
        if (filledSegments < 1) return

        val startRad = toRadians(startAngle)
        val endRad = toRadians(endAngle)
        val totalAngle = (endRad - startRad) * fill
        val segmentAngle = totalAngle / filledSegments

        val innerRadius = radius - thickness

        for (i in 0 until filledSegments) {
            val a0 = startRad + i * segmentAngle
            val a1 = a0 + segmentAngle

            val cos0 = cos(a0)
            val sin0 = sin(a0)
            val cos1 = cos(a1)
            val sin1 = sin(a1)

            val ox0 = cos0 * radius + x
            val oy0 = sin0 * radius + y
            val ix0 = cos0 * innerRadius + x
            val iy0 = sin0 * innerRadius + y

            val ox1 = cos1 * radius + x
            val oy1 = sin1 * radius + y
            val ix1 = cos1 * innerRadius + x
            val iy1 = sin1 * innerRadius + y

            // треугольник 1
            vertices.vertex(pose, ox0, oy0).color(color)
            vertices.vertex(pose, ix0, iy0).color(color)
            vertices.vertex(pose, ix1, iy1).color(color)

            // треугольник 2
            vertices.vertex(pose, ox0, oy0).color(color)
            vertices.vertex(pose, ix1, iy1).color(color)
            vertices.vertex(pose, ox1, oy1).color(color)
        }
    }


    override fun pipeline(): RenderPipeline = pipeline

    override fun textureSetup(): TextureSetup = textureSetup

    override fun scissorArea(): ScreenRect? = scissorArea

    override fun bounds(): ScreenRect = bounds

    companion object {
        private fun createBounds(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            pose: Matrix3x2f,
            scissorArea: ScreenRect?
        ): ScreenRect {
            val screenRect = ScreenRect(
                ceil(x0).toInt(),
                ceil(y0).toInt(),
                ceil(x1 - x0).toInt(),
                ceil(y1 - y0).toInt()
            ).transformEachVertex(pose)
            return if (scissorArea != null) scissorArea.intersection(screenRect)!! else screenRect
        }
    }
}

data class EngineGuiTexturedQuad(
    val pipeline: RenderPipeline,
    val textureSetup: TextureSetup,
    val pose: Matrix3x2f,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val u1: Float,
    val u2: Float,
    val v1: Float,
    val v2: Float,
    val color: Int,
    val scissorArea: ScreenRect?,
    val bounds: ScreenRect = createBounds(x1, y1, x1, y1, pose, scissorArea)
) : SimpleGuiElementRenderState {
    override fun setupVertices(vertices: VertexConsumer) {
        vertices.vertex(pose, x1, y1).texture(u1, v1).color(color)
        vertices.vertex(pose, x1, y2).texture(u1, v2).color(color)
        vertices.vertex(pose, x2, y2).texture(u2, v2).color(color)
        vertices.vertex(pose, x2, y1).texture(u2, v1).color(color)
    }

    override fun pipeline(): RenderPipeline = pipeline

    override fun textureSetup(): TextureSetup = textureSetup

    override fun scissorArea(): ScreenRect? = scissorArea

    override fun bounds(): ScreenRect = bounds
}