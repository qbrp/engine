package org.lain.engine.client.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.ARBImaging
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30

/** Copies color while preserving the caller's read and draw framebuffer bindings. */
fun RenderTarget.copyColorTo(destination: RenderTarget) {
    RenderSystem.assertOnRenderThread()
    val readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
    val drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
    try {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, frameBufferId)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId)
        GlStateManager._glBlitFrameBuffer(
            0, 0, width, height,
            0, 0, destination.width, destination.height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST,
        )
    } finally {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
    }
}

/**
 * Cross-fades [source] over this target using a constant opacity, ignoring source alpha.
 * Restores blend and depth state and leaves this target bound for writing.
 */
fun RenderTarget.blendFrom(source: RenderTarget, opacity: Float) {
    blitWithBlend({ source }) {
        GL14.glBlendColor(1f, 1f, 1f, opacity.coerceIn(0f, 1f))
        RenderSystem.blendFunc(GL14.GL_CONSTANT_ALPHA, GL14.GL_ONE_MINUS_CONSTANT_ALPHA)
    }
}

/**
 * Renders a layer and composites its premultiplied color over this target.
 * Saves blend and depth state before [renderSource] and restores it even if rendering fails.
 * Leaves this target bound for writing.
 */
fun RenderTarget.blendPremultipliedFrom(renderSource: () -> RenderTarget) {
    blitWithBlend(renderSource) {
        // Rendering into transparent black already multiplied RGB by alpha.
        // Multiplying it again would darken translucent text and backgrounds.
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
        )
    }
}

private inline fun RenderTarget.blitWithBlend(
    renderSource: () -> RenderTarget,
    configureBlend: () -> Unit,
) {
    RenderSystem.assertOnRenderThread()
    val depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
    val depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
    val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
    val blend = GL11.glIsEnabled(GL11.GL_BLEND)
    val sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
    val destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
    val sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
    val destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
    val blendColor = FloatArray(4)
    GL11.glGetFloatv(ARBImaging.GL_BLEND_COLOR, blendColor)
    try {
        val source = renderSource()
        bindWrite(true)
        RenderSystem.enableBlend()
        configureBlend()
        source.blitToScreen(viewWidth, viewHeight, false)
    } finally {
        bindWrite(true)
        RenderSystem.depthMask(depthWrite)
        RenderSystem.depthFunc(depthFunction)
        if (depthTest) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
        GL14.glBlendColor(blendColor[0], blendColor[1], blendColor[2], blendColor[3])
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
        if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
    }
}
