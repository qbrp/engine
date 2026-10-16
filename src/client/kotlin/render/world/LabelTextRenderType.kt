package org.lain.engine.client.render.world

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import org.lain.engine.mc.engineId
import java.util.concurrent.ConcurrentHashMap

private const val LABEL_TEXT_BUFFER_SIZE = 1536
private val LABEL_TEXT_SHADER_ID = engineId("label_text")
private val LABEL_TEXT_INTENSITY_SHADER_ID = engineId("label_text_intensity")

object LabelTextRenderTypes {
    @Volatile
    private var colorShader: ShaderInstance? = null

    @Volatile
    private var intensityShader: ShaderInstance? = null

    private val colorShaderState = RenderStateShard.ShaderStateShard {
        checkNotNull(colorShader) { "Label text shader has not been loaded" }
    }
    private val intensityShaderState = RenderStateShard.ShaderStateShard {
        checkNotNull(intensityShader) { "Label text intensity shader has not been loaded" }
    }

    private val replacements = ConcurrentHashMap<RenderType, RenderType>()
    private val colorRenderTypes = ConcurrentHashMap<ResourceLocation, RenderType>()
    private val intensityRenderTypes = ConcurrentHashMap<ResourceLocation, RenderType>()

    fun registerShaders() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(LABEL_TEXT_SHADER_ID, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
                colorShader = it
            }
            context.register(LABEL_TEXT_INTENSITY_SHADER_ID, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
                intensityShader = it
            }
        }
    }

    @JvmStatic
    fun registerColorTexture(vanillaRenderType: RenderType, texture: ResourceLocation) {
        replacements[vanillaRenderType] = colorRenderTypes.computeIfAbsent(texture) {
            createRenderType("engine_label_text", it, colorShaderState)
        }
    }

    @JvmStatic
    fun registerIntensityTexture(vanillaRenderType: RenderType, texture: ResourceLocation) {
        replacements[vanillaRenderType] = intensityRenderTypes.computeIfAbsent(texture) {
            createRenderType("engine_label_text_intensity", it, intensityShaderState)
        }
    }

    fun replace(renderType: RenderType): RenderType {
        return replacements[renderType] ?: renderType
    }

    private fun createRenderType(
        name: String,
        texture: ResourceLocation,
        shader: RenderStateShard.ShaderStateShard
    ): RenderType {
        return RenderType.create(
            name,
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            LABEL_TEXT_BUFFER_SIZE,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(shader)
                .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false)
        )
    }
}

internal class LabelTextBufferSource(
    private val delegate: MultiBufferSource
) : MultiBufferSource {
    override fun getBuffer(renderType: RenderType): VertexConsumer {
        return delegate.getBuffer(LabelTextRenderTypes.replace(renderType))
    }
}
