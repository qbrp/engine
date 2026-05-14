package org.lain.engine.client.render.ui

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.state.GuiElementRenderState
import org.joml.Matrix3x2f
import kotlin.math.ceil

private fun createBounds(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    pose: Matrix3x2f,
    scissorArea: ScreenRectangle?
): ScreenRectangle {
    val screenRect = ScreenRectangle(
        ceil(x0).toInt(),
        ceil(y0).toInt(),
        ceil(x1 - x0).toInt(),
        ceil(y1 - y0).toInt()
    ).transformMaxBounds(pose)
    return if (scissorArea != null) scissorArea.intersection(screenRect) ?: ScreenRectangle(0, 0, 0, 0) else screenRect
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
    val scissorArea: ScreenRectangle? = null,
    val bounds: ScreenRectangle = createBounds(x0, y0, x1, y1, pose, scissorArea)
) : GuiElementRenderState {
    override fun buildVertices(vertices: VertexConsumer) {
        vertices.addVertexWith2DPose(pose, x0, y0).setColor(col1)
        vertices.addVertexWith2DPose(pose, x0, y1).setColor(col2)
        vertices.addVertexWith2DPose(pose, x1, y1).setColor(col2)
        vertices.addVertexWith2DPose(pose, x1, y0).setColor(col1)
    }

    override fun pipeline(): RenderPipeline = pipeline

    override fun textureSetup(): TextureSetup = textureSetup

    override fun scissorArea(): ScreenRectangle? = scissorArea

    override fun bounds(): ScreenRectangle = bounds
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
    val scissorArea: ScreenRectangle?,
    val bounds: ScreenRectangle = createBounds(x1, y1, x1, y1, pose, scissorArea)
) : GuiElementRenderState {
    override fun buildVertices(vertices: VertexConsumer) {
        vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color)
        vertices.addVertexWith2DPose(pose, x1, y2).setUv(u1, v2).setColor(color)
        vertices.addVertexWith2DPose(pose, x2, y2).setUv(u2, v2).setColor(color)
        vertices.addVertexWith2DPose(pose, x2, y1).setUv(u2, v1).setColor(color)
    }

    override fun pipeline(): RenderPipeline = pipeline

    override fun textureSetup(): TextureSetup = textureSetup

    override fun scissorArea(): ScreenRectangle? = scissorArea

    override fun bounds(): ScreenRectangle = bounds
}