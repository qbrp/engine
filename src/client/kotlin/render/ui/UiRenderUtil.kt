package org.lain.engine.client.render.ui

import net.minecraft.CrashReport
import net.minecraft.ReportedException
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.LittleNotification
import org.lain.engine.mc.Text
import org.lain.engine.mc.ecs.ITEM_STACK_MATERIAL
import org.lain.engine.mc.engineId
import org.lain.engine.mc.literalText
import org.lain.engine.util.Color

object ColorMc {
    fun color(alpha: Float, color: Int): Int = color((alpha * 255f).toInt(), color)

    fun color(alpha: Int, color: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    fun color(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    fun alpha(color: Int): Int = color ushr 24
    fun red(color: Int): Int = color shr 16 and 0xFF
    fun green(color: Int): Int = color shr 8 and 0xFF
    fun blue(color: Int): Int = color and 0xFF
}

// А почему бы и нет?
private val ENGINE_SPRITE_CACHE = mutableMapOf<String, ResourceLocation>()

val LittleNotification.titleText: Text
    get() = literalText(title).withColor(color.integer)

val LittleNotification.descriptionText: Text?
    get() = description?.let { literalText(it) }

fun GuiGraphics.drawEngineSprite(
    sprite: EngineSprite,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    color: Color = Color.WHITE
) {
    drawTexturedQuad(
        ENGINE_SPRITE_CACHE.getOrPut(sprite.path) { engineId(sprite.path) },
        x, x + width,
        y, y + height,
        sprite.u1, sprite.u2,
        sprite.v1, sprite.v2,
        color
    )
}

fun GuiGraphics.drawTintedSprite(
    sprite: ResourceLocation,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    color: Int
) {
    setColor(
        ColorMc.red(color) / 255f,
        ColorMc.green(color) / 255f,
        ColorMc.blue(color) / 255f,
        ColorMc.alpha(color) / 255f
    )
    blitSprite(sprite, x, y, width, height)
    setColor(1f, 1f, 1f, 1f)
}

fun GuiGraphics.drawTexturedQuad(
    texture: ResourceLocation,
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
    flush()
    RenderSystem.setShaderTexture(0, texture)
    RenderSystem.setShader(GameRenderer::getPositionTexShader)
    RenderSystem.setShaderColor(
        color.r / 255f,
        color.g / 255f,
        color.b / 255f,
        color.a / 255f
    )
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()
    val matrix = pose().last().pose()
    val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
    builder.addVertex(matrix, x1, y1, 0f).setUv(u1, v1)
    builder.addVertex(matrix, x1, y2, 0f).setUv(u1, v2)
    builder.addVertex(matrix, x2, y2, 0f).setUv(u2, v2)
    builder.addVertex(matrix, x2, y1, 0f).setUv(u2, v1)
    BufferUploader.drawWithShader(builder.buildOrThrow())
    RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    RenderSystem.disableBlend()
}

fun GuiGraphics.fill(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, color2: Int = color) {
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

    val matrix = pose().last().pose()
    val vertices = bufferSource().getBuffer(RenderType.gui())
    vertices.addVertex(matrix, x1, y1, 0f).setColor(color)
    vertices.addVertex(matrix, x1, y2, 0f).setColor(color2)
    vertices.addVertex(matrix, x2, y2, 0f).setColor(color2)
    vertices.addVertex(matrix, x2, y1, 0f).setColor(color)
    flush()
}

fun GuiGraphics.drawFakeEngineItem(model: ResourceLocation, name: String, x: Int, y: Int) {
    val bakedModel = MinecraftClient.modelManager.getModel(model)
    pose().pushPose()
    pose().translate((x + 8).toDouble(), (y + 8).toDouble(), 150.0)
    try {
        pose().scale(16f, -16f, 16f)
        val flatLighting = !bakedModel.usesBlockLight()
        if (flatLighting) Lighting.setupForFlatItems()
        MinecraftClient.itemRenderer.render(
            ITEM_STACK_MATERIAL,
            net.minecraft.world.item.ItemDisplayContext.GUI,
            false,
            pose(),
            bufferSource(),
            0xF000F0,
            OverlayTexture.NO_OVERLAY,
            bakedModel
        )
        flush()
        if (flatLighting) Lighting.setupFor3DItems()
    } catch (throwable: Throwable) {
        val crashReport = CrashReport.forThrowable(throwable, "Rendering fake enigine item")
        crashReport.addCategory("Item being rendered").setDetail("Model", "$model ($name)")
        throw ReportedException(crashReport)
    } finally {
        pose().popPose()
    }
}
