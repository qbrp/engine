package org.lain.engine.client.mc

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.font.TextRenderer.GlyphDrawable
import net.minecraft.client.gui.ScreenRect
import net.minecraft.client.gui.render.state.GuiElementRenderState
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.texture.TextureSetup
import net.minecraft.text.OrderedText
import org.joml.Matrix3x2f
import org.lain.engine.util.toRadians
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
        val segmentAngle = (endRad - startRad) * fill / filledSegments

        val innerRadius = radius - thickness

        for (i in 0 until filledSegments) {
            val angle = startRad + i * segmentAngle

            val x1 = cos(angle) * radius + x
            val y1 = sin(angle) * radius + y
            val x2 = cos(angle) * innerRadius + x
            val y2 = sin(angle) * innerRadius + y

            vertices.vertex(pose, x1, y1).color(color)
            vertices.vertex(pose, x2, y2).color(color)
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