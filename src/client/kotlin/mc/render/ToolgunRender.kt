package org.lain.engine.client.mc.render

import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState
import net.minecraft.client.render.*
import net.minecraft.client.render.command.RenderCommandQueue
import net.minecraft.client.render.command.RenderDispatcher
import net.minecraft.client.texture.TextureSetup
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Colors
import org.joml.Vector3f
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.util.EngineId

class ToolgunRenderer(
    private val vertexConsumers: VertexConsumerProvider.Immediate,
    private val dispatcher: RenderDispatcher
) {
    private lateinit var screenAtlasTexture: GpuTexture
    private lateinit var screenAtlasTextureView: GpuTextureView
    private lateinit var screenRenderLayer: RenderLayer
    private val screenProjectionMatrix = ProjectionMatrix2("toolgun screen", -1000.0F, 1000.0F, true);

    fun init() {
        val gpuDevice = RenderSystem.getDevice()
        screenAtlasTexture = gpuDevice.createTexture("Engine screen atlas", 12, TextureFormat.RGBA8, 256, 256, 1, 1)
        val texture = MinecraftClient.textureManager.registerTexture(EngineId("scree"))
        screenAtlasTexture.setTextureFilter(FilterMode.NEAREST, false)
        screenAtlasTextureView = gpuDevice.createTextureView(screenAtlasTexture)
        screenRenderLayer = RenderLayer.of(
            "toolgun_screen",
            1536,
            false,
            false,
            RenderPipelines.GUI_TEXTURED,
            RenderLayer.MultiPhaseParameters.builder()
                .texture(
                    RenderPhase.TextureBase({ RenderSystem.setShaderTexture(0, screenAtlasTextureView) }, {})
                )
                .target(RenderPhase.MAIN_TARGET)
                .build(true)
        )
    }

    fun renderInTexture() {
        RenderSystem.outputColorTextureOverride = screenAtlasTextureView
        RenderSystem.setProjectionMatrix(
            screenProjectionMatrix.set(256f, 256f),
            ProjectionType.ORTHOGRAPHIC
        )
        MinecraftClient.gameRenderer.diffuseLighting.setShaderLights(DiffuseLighting.Type.ITEMS_3D)
        RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorTexture(screenAtlasTexture, Colors.LIGHT_GRAY)
        val matrix = MatrixStack()
        dispatcher.queue.submitText(
            matrix,
            0f,
            0f,
            Text.of("toolgun.exe").asOrderedText(),
            true,
            TextRenderer.TextLayerType.NORMAL,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            Colors.WHITE,
            Colors.BLACK,
            Colors.DARK_GRAY
        )
        dispatcher.render()
        vertexConsumers.draw();
        RenderSystem.outputColorTextureOverride = null
    }

    fun renderScreenTest(ctx: DrawContext) {
        ctx.state.addSimpleElement(
            TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.withoutGlTexture(screenAtlasTextureView),
                ctx.matrices,
                0,
                0,
                256,
                256,
                0f,
                1f,
                0f,
                1f,
                -1,
                ctx.scissorStack.peekLast(),
                null
            )
        )
    }

    fun renderScreenQuad(
        matrixStack: MatrixStack,
        queue: RenderCommandQueue,
        vec1: Vector3f,
        vec2: Vector3f,
        vec3: Vector3f,
        vec4: Vector3f,
    ) {
        queue.submitCustom(matrixStack, screenRenderLayer) { matrices, vertexConsumer ->
            val matrix = matrices.positionMatrix
            vertexConsumer
                .vertex(matrix, vec1.x, vec1.y, vec1.z)
                .texture(0f, 0f)
                .color(Colors.WHITE)
            vertexConsumer
                .vertex(matrix, vec2.x, vec2.y, vec2.z)
                .texture(1f, 0f)
                .color(Colors.WHITE)
            vertexConsumer
                .vertex(matrix, vec3.x, vec3.y, vec3.z)
                .texture(0f, 1f)
                .color(Colors.WHITE)
            vertexConsumer
                .vertex(matrix, vec4.x, vec4.y, vec4.z)
                .texture(1f, 1f)
                .color(Colors.WHITE)
        }
    }
}