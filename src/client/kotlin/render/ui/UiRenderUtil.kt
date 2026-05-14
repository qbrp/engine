package org.lain.engine.client.render.ui

import net.minecraft.CrashReport
import net.minecraft.ReportedException
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.state.GuiItemRenderState
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.joml.Matrix3x2f
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.client.render.item.updateItemRenderState
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.mc.Text
import org.lain.engine.mc.engineId
import org.lain.engine.mc.literalText
import org.lain.engine.util.Color

typealias ColorMc = ARGB

// А почему бы и нет?
private val ENGINE_SPRITE_CACHE = mutableMapOf<String, Identifier>()

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

fun GuiGraphics.drawTexturedQuad(
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
    val gpuTexture = MinecraftClient.textureManager.getTexture(texture)
    guiRenderState.submitGuiElement(
        EngineGuiTexturedQuad(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTextureWithLightmap(gpuTexture.textureView, gpuTexture.sampler),
            Matrix3x2f(this.pose()),
            x1, y1,
            x2, y2,
            u1, u2,
            v1, v2,
            color.integer,
            scissorStack.peek()
        )
    )
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

    guiRenderState.submitGuiElement(
        EngineGuiQuad(
            RenderPipelines.GUI,
            TextureSetup.noTexture(),
            Matrix3x2f(this.pose()),
            x1,
            y1,
            x2,
            y2,
            color,
            color2,
            scissorStack.peek()
        )
    )
}

fun GuiGraphics.drawFakeEngineItem(model: Identifier, name: String, x: Int, y: Int) {
    val keyedItemRenderState = TrackingItemStackRenderState()
    updateItemRenderState(keyedItemRenderState, model, false, EngineItemDisplayContext.GUI, null, null, 0)
    try {
        guiRenderState.submitItem(
            GuiItemRenderState(
                name,
                Matrix3x2f(this.pose()),
                keyedItemRenderState,
                x,
                y,
                this.scissorStack.peek()
            )
        )
    } catch (throwable: Throwable) {
        val crashReport = CrashReport.forThrowable(throwable, "Rendering fake enigine item")
        crashReport.addCategory("Item being rendered")
        throw ReportedException(crashReport)
    }
}
