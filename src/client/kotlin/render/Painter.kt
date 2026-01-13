package org.lain.engine.client.render

import org.lain.engine.util.Color
import org.lain.engine.util.text.EngineOrderedText
import org.lain.engine.util.text.EngineOrderedTextSequence
import org.lain.engine.util.text.EngineText

data class EngineSprite(
    val path: String,
    val resolution: Int,
    val v1: Float = 0f,
    val u1: Float = 0f,
    val v2: Float = 1f,
    val u2: Float = 1f,
)

interface FontRenderer {
    val fontHeight: Float
    fun getWidth(text: EngineOrderedTextSequence): Float
    fun breakTextByLines(text: EngineText, width: Float): List<EngineOrderedTextSequence>
}

interface Painter : FontRenderer {
    val tickDelta: Float

    // Примитивы
    fun fill(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, color2: Color = color)

    // Фигуры

    /**
     * Рисует две дуги с заполненным между ними пространством.
     */
    fun drawArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        thickness: Float,
        color: Color,
        fill: Float = 1f,
        startAngle: Float = 0f,
        endAngle: Float = 360f,
        segments: Int = 150,
    )

    // Текстуры
    fun drawSprite(
        sprite: EngineSprite,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    )

    fun push()
    fun translate(x: Float, y: Float)
    fun scale(x: Float, y: Float)
    fun pop()
}
