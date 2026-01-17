package org.lain.engine.client.mc.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.texture.TextureSetup
import net.minecraft.util.Identifier
import org.joml.Matrix3x2f
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.Painter
import org.lain.engine.util.*
import org.lain.engine.util.text.EngineTextSpan
import org.lain.engine.util.text.EngineOrderedText
import org.lain.engine.util.text.EngineText
import org.lain.engine.util.text.splitEngineTextLinear
import org.lain.engine.util.text.toMinecraft

// А почему бы и нет?
private val ENGINE_SPRITE_CACHE = mutableMapOf<String, Identifier>()

fun DrawContext.drawEngineSprite(
    sprite: EngineSprite,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    color: Color = Color.WHITE
) {
    drawTexturedQuad(
        ENGINE_SPRITE_CACHE.getOrPut(sprite.path) { EngineId(sprite.path) },
        x, x + width,
        y, y + height,
        sprite.u1, sprite.u2,
        sprite.v1, sprite.v2,
        color
    )
}

fun DrawContext.drawTexturedQuad(
    texture: Identifier,
    x1: Float,
    x2: Float,
    y1: Float,
    y2: Float,
    u1: Float,
    u2: Float,
    v1: Float,
    v2: Float,
    color: Color = Color.WHITE
) {
    val gpuTextureView = MinecraftClient.textureManager.getTexture(texture).getGlTextureView()
    state.addSimpleElement(
        EngineGuiTexturedQuad(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.withoutGlTexture(gpuTextureView),
            Matrix3x2f(this.matrices),
            x1, y1,
            x2, y2,
            u1, u2,
            v1, v2,
            color.integer,
            scissorStack.peekLast()
        )
    )
}

fun DrawContext.fill(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, color2: Int) {
    val (x1, x2) = if (x1 < x2) {
        x2 to x1
    } else {
        x1 to x2
    }

    val (y1, y2) = if (y1 < y2) {
        y2 to y1
    } else {
        y1 to y2
    }

    state.addSimpleElement(
        EngineGuiQuad(
            RenderPipelines.GUI,
            TextureSetup.empty(),
            Matrix3x2f(this.matrices),
            x1,
            y1,
            x2,
            y2,
            color,
            color2,
            scissorStack.peekLast()
        )
    )
}

class MinecraftFontRenderer : FontRenderer {
    internal val minecraftTextRenderer
        get() = MinecraftClient.textRenderer
    override val fontHeight: Float
        get() = minecraftTextRenderer.fontHeight.toFloat()

    override fun getWidth(text: EngineOrderedText): Float {
        return text.parts.sumOf { minecraftTextRenderer.getWidth(it.toMinecraft()).toFloat() }
    }

    override fun breakTextByLines(
        text: EngineText,
        width: Float
    ): List<EngineOrderedText> {
        val lines = mutableListOf<EngineOrderedText>()
        val split = splitEngineTextLinear(text, " ")

        var x = 0
        val parts = mutableListOf<EngineTextSpan>()

        for (word in split) {
            val wordWidth = minecraftTextRenderer.getWidth(word.toMinecraft())
            if (x > 0 && x + wordWidth > width) {
                lines += EngineOrderedText(parts.toList())
                parts.clear()
                x = 0
            }
            parts += word
            x += wordWidth
        }
        if (parts.isNotEmpty()) {
            lines += EngineOrderedText(parts.toList())
        }

        return lines
    }
}

class MinecraftPainter(
    override val tickDelta: Float,
    val context: DrawContext,
    val textRenderer: MinecraftFontRenderer,
) : Painter, FontRenderer by textRenderer {
    private val matrices get() = context.matrices
    private val camera get() = MinecraftClient.gameRenderer.camera

    override fun fill(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, color2: Color) {
        context.fill(x1, y1, x2, y2, color.integer, color2.integer)
    }

    override fun drawArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        thickness: Float,
        color: Color,
        fill: Float,
        startAngle: Float,
        endAngle: Float,
        segments: Int
    ) {
        context.state.addSimpleElement(
            EngineGuiArc(
                RenderPipelines.DEBUG_FILLED_BOX,
                TextureSetup.empty(),
                centerX,
                centerY,
                matrices,
                color.integer,
                startAngle,
                endAngle,
                radius,
                thickness,
                segments,
                fill
            )
        )
    }

    override fun drawSprite(sprite: EngineSprite, x: Float, y: Float, width: Float, height: Float) {
        context.drawEngineSprite(sprite, x, y, width, height)
    }

    override fun push() {
        matrices.pushMatrix()
    }

    override fun translate(x: Float, y: Float) {
        matrices.translate(x, y)
    }

    override fun pop() {
        matrices.popMatrix()
    }

    override fun scale(x: Float, y: Float) {
        matrices.scale(x, y)
    }

    companion object {
        val GUI_SNIPPET: RenderPipeline.Snippet =
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withVertexShader("core/gui")
                .withFragmentShader("core/gui")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .buildSnippet()
        val GUI_TRIANGLE_STRIP = RenderPipeline.builder(GUI_SNIPPET)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
            .withEngineLocation("pipeline/gui")
            .withCull(false)
            .build()

        fun RenderPipeline.Builder.withEngineLocation(id: String) = withLocation(EngineId(id))
    }
}
