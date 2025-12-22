package org.lain.engine.client.mc

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.ShaderProgramKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.*
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.*
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import org.lain.engine.client.render.*
import org.lain.engine.util.EngineId
import org.lain.engine.util.toRadians
import java.util.*
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

fun drawSprite(
    matrices: MatrixStack,
    vertexConsumers: VertexConsumerProvider.Immediate,
    sprite: EngineSprite,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    z: Float
) {
    val identifier = EngineId(sprite.path)
    drawTexturedQuad(
        matrices,
        vertexConsumers,
        identifier,
        x,
        x + width,
        y,
        y + height,
        z,
        sprite.u1,
        sprite.u2,
        sprite.v1,
        sprite.v2
    )
}

fun drawTexturedQuad(
    matrices: MatrixStack,
    vertexConsumers: VertexConsumerProvider.Immediate,
    texture: Identifier?,
    x1: Float,
    x2: Float,
    y1: Float,
    y2: Float,
    z: Float,
    u1: Float,
    u2: Float,
    v1: Float,
    v2: Float,
    color: Int = WHITE
) {
    val matrix4f = matrices.peek().getPositionMatrix()
    val vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getGuiTextured(texture))
    vertexConsumer.vertex(matrix4f, x1.toFloat(), y1.toFloat(), z).texture(u1, v1).color(color)
    vertexConsumer.vertex(matrix4f, x1.toFloat(), y2.toFloat(), z).texture(u1, v2).color(color)
    vertexConsumer.vertex(matrix4f, x2.toFloat(), y2.toFloat(), z).texture(u2, v2).color(color)
    vertexConsumer.vertex(matrix4f, x2.toFloat(), y1.toFloat(), z).texture(u2, v1).color(color)
}

class MinecraftFontRenderer() : FontRenderer {
    internal val minecraftTextRenderer get() = MinecraftClient.textRenderer

    override fun getTextHeight(text: EngineText, settings: TextRenderSettings): Float {
        val mcText = MinecraftText(text)
        var y = 0f
        textWithSettings(mcText, settings).forEach { line ->
            y += minecraftTextRenderer.fontHeight * text.scale
        }
        return y
    }

    fun textWithSettings(
        text: MinecraftText,
        settings: TextRenderSettings
    ): List<OrderedText> {
        val wrap = settings.wrap

        val lines = mutableListOf<OrderedText>()
        if (wrap != null) {
            lines.addAll(minecraftTextRenderer.wrapLines(text, wrap.toInt()))
        } else {
            lines.add(text.asOrderedText())
        }

        return lines
    }
}

class MinecraftPainter(
    override val tickDelta: Float,
    val matrices: MatrixStack,
    val vertexConsumers: VertexConsumerProvider.Immediate,
    val textRenderer: MinecraftFontRenderer,
) : Painter, FontRenderer by textRenderer {
    private val camera get() = MinecraftClient.gameRenderer.camera

    override fun enableScissor(x1: Float, y1: Float, x2: Float, y2: Float) {
        TODO("Not yet implemented")
    }

    override fun disableScissor() {
        TODO("Not yet implemented")
    }

    override fun fill(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, z: Float, layer: RenderLayer) {
        val matrix4f= this.matrices.peek().getPositionMatrix()

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

        val vertexConsumer = vertexConsumers.getBuffer(layer)
        vertexConsumer.vertex(matrix4f, x1, y1, z).color(color)
        vertexConsumer.vertex(matrix4f, x1, y2, z).color(color)
        vertexConsumer.vertex(matrix4f, x2, y2, z).color(color)
        vertexConsumer.vertex(matrix4f, x2, y1, z).color(color)
    }

    override fun fillGradientVertical(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        z: Float,
        startColor: Int,
        endColor: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun fillGradientHorizontal(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        z: Float,
        startColor: Int,
        endColor: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun drawBorder(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        z: Float,
        color: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun drawArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        thickness: Float,
        color: Int,
        fill: Float,
        startAngle: Float,
        endAngle: Float,
        segments: Int
    ) {
        val filledSegments = ceil(segments * fill).toInt()
        if (filledSegments < 1) return

        matrices.push()
        matrices.translate(centerX, centerY, 0f)

        val matrix = matrices.peek().positionMatrix

        GlStateManager._depthMask(false)
        GlStateManager._disableCull()
        RenderSystem.enableBlend()
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR)

        val tessellator = RenderSystem.renderThreadTesselator()
        val buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR)

        val startRad = toRadians(startAngle.toDouble())
        val endRad = toRadians(endAngle.toDouble())
        val segmentAngle = (endRad - startRad) / segments

        val innerRadius = radius - thickness

        for (i in 0..filledSegments) {
            val angle = startRad + i * segmentAngle

            val x1 = cos(angle) * radius
            val y1 = sin(angle) * radius
            val x2 = cos(angle) * innerRadius
            val y2 = sin(angle) * innerRadius

            buffer.vertex(matrix, x1.toFloat(), y1.toFloat(), 0f).color(color)
            buffer.vertex(matrix, x2.toFloat(), y2.toFloat(), 0f).color(color)
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end())

        RenderSystem.disableBlend()
        GlStateManager._enableCull()
        GlStateManager._depthMask(true)

        matrices.pop()
    }

    override fun drawSprite(sprite: EngineSprite, x: Float, y: Float, width: Float, height: Float, z: Float) {
        val identifier = EngineId(sprite.path)
        drawTexturedQuad(
            identifier,
            x,
            x + width,
            y,
            y + height,
            z,
            sprite.u1,
            sprite.u2,
            sprite.v1,
            sprite.v2
        )
    }

    private fun drawTexturedQuad(
        texture: Identifier?,
        x1: Float,
        x2: Float,
        y1: Float,
        y2: Float,
        z: Float,
        u1: Float,
        u2: Float,
        v1: Float,
        v2: Float,
        color: Int = WHITE
    ) {
        val matrix4f = this.matrices.peek().getPositionMatrix()
        val vertexConsumer = this.vertexConsumers.getBuffer(RenderLayer.getGuiTextured(texture))
        vertexConsumer.vertex(matrix4f, x1.toFloat(), y1.toFloat(), z).texture(u1, v1).color(color)
        vertexConsumer.vertex(matrix4f, x1.toFloat(), y2.toFloat(), z).texture(u1, v2).color(color)
        vertexConsumer.vertex(matrix4f, x2.toFloat(), y2.toFloat(), z).texture(u2, v2).color(color)
        vertexConsumer.vertex(matrix4f, x2.toFloat(), y1.toFloat(), z).texture(u2, v1).color(color)
    }

    override fun drawText(
        text: EngineText,
        x: Float,
        y: Float,
        settings: TextRenderSettings,
        z: Float,
        backgroundOpacity: Int,
        light: Int
    ): Vec2 {
        val mcText = MinecraftText(text)
        val lines = textRenderer.textWithSettings(mcText, settings)

        matrices.push()
        matrices.scale(text.scale, text.scale, 0f)

        var currentY = y
        var lastX = x

        for (line in lines) {
            lastX = max(
                textRenderer.minecraftTextRenderer.draw(
                    line,
                    x / text.scale,
                    currentY / text.scale,
                    DEFAULT_TEXT_COLOR,
                    true,
                    matrices.peek().getPositionMatrix(),
                    this.vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL,
                    backgroundOpacity,
                    light
                ).toFloat(),
                lastX
            )

            currentY += getTextHeight(text)
        }

        matrices.pop()

        return ImmutableVec2(lastX, currentY)
    }

    override fun push() {
        matrices.push()
    }

    override fun translate(x: Float, y: Float, z: Float) {
        matrices.translate(x, y, z)
    }

    override fun pop() {
        matrices.pop()
    }

    override fun multiply(rotation: Quaternionf) {
        matrices.multiply(rotation)
    }

    override fun scale(x: Float, y: Float, z: Float) {
        matrices.scale(x, y, z)
    }
}

class MinecraftText(val text: EngineText): Text {
    private val _style = Style.EMPTY
        .withColor(text.color ?: DEFAULT_TEXT_COLOR)
        .withItalic(text.italic)
        .withBold(text.bold)
        .withUnderline(text.underline)
        .withStrikethrough(text.strike)
    private val _content = MinecraftTextContent(this)

    override fun getStyle(): Style = _style

    override fun getContent(): TextContent = _content

    override fun getSiblings(): List<Text> = listOf()

    override fun asOrderedText(): OrderedText = OrderedText.styledForwardsVisitedString(text.content, _style)
}

class MinecraftTextContent(
    val text: MinecraftText
) : TextContent {
    override fun getType(): TextContent.Type<*> = TYPE

    override fun <T : Any?> visit(visitor: StringVisitable.StyledVisitor<T>, style: Style): Optional<T?>? {
        return visitor.accept(text.style, text.text.content)
    }

    override fun <T : Any> visit(visitor: StringVisitable.Visitor<T?>): Optional<T?> {
        return visitor.accept(text.text.content)
    }

    companion object {
        val TYPE = TextContent.Type<MinecraftTextContent>(
            RecordCodecBuilder.mapCodec { instance ->
                instance
                    .group(
                        Codec.STRING.fieldOf("text").forGetter { it.text.text.content },
                        Codec.INT.fieldOf("color").forGetter { it.text.text.color },
                        Codec.FLOAT.fieldOf("scale").forGetter { it.text.text.scale },
                    )
                    .apply(instance) { text, color, scale ->
//                        MinecraftTextContent(
//                            MinecraftText(text, color, scale)
//                        )
                        TODO()
                    }
            },
            null
        )
    }
}
