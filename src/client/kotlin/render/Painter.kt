package org.lain.engine.client.render

import net.minecraft.client.render.RenderLayer
import org.joml.Quaternionf
import org.lain.engine.client.chat.EngineChatMessage
import org.lain.engine.player.Player
import org.lain.engine.util.Pos

data class EngineText(
    val content: String,
    val color: Int? = null,
    val scale: Float = 1f,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false
)

data class TextRenderSettings(val wrap: Float? = null) {
    companion object {
        val DEFAULT = TextRenderSettings()
    }
}

data class EngineSprite(
    val path: String,
    val resolution: Int,
    val v1: Float = 0f,
    val u1: Float = 0f,
    val v2: Float = 1f,
    val u2: Float = 1f,
)

interface FontRenderer {
    fun getTextHeight(
        text: EngineText,
        settings: TextRenderSettings = TextRenderSettings.DEFAULT
    ): Float
}

interface Painter : FontRenderer {
    val tickDelta: Float

    // Scissor
    fun enableScissor(x1: Float, y1: Float, x2: Float, y2: Float)
    fun disableScissor()

    // Примитивы
    fun fill(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, z: Float = 0f, layer: RenderLayer = RenderLayer.getGui())
    fun fillGradientVertical(x1: Float, y1: Float, x2: Float, y2: Float, z: Float = 0f, startColor: Int, endColor: Int)
    fun fillGradientHorizontal(x1: Float, y1: Float, x2: Float, y2: Float, z: Float = 0f, startColor: Int, endColor: Int)
    fun drawBorder(x: Float, y: Float, width: Float, height: Float, z: Float = 0f, color: Int)

    // Фигуры

    /**
     * Рисует две дуги с заполненным между ними пространством.
     */
    fun drawArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        thickness: Float,
        color: Int,
        fill: Float = 1f,
        startAngle: Float = 0f,
        endAngle: Float = 360f,
        segments: Int = 80,
    )

    // Текстуры
    fun drawSprite(
        sprite: EngineSprite,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        z: Float = 0f,
    )

    // Текст
    fun drawText(
        text: EngineText,
        x: Float,
        y: Float,
        settings: TextRenderSettings = TextRenderSettings.DEFAULT,
        z: Float = 0f,
        backgroundOpacity: Int = 0,
        light: Int = 0xF000F0
    ): Vec2

    fun push()
    fun translate(x: Float, y: Float, z: Float = 0f)
    fun multiply(rotation: Quaternionf)
    fun scale(x: Float, y: Float, z: Float)
    fun pop()
}
