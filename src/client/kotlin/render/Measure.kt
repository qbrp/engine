package org.lain.engine.client.render

import org.lain.engine.util.math.sumOf

data class Rect2(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

data class Size(val width: Float, val height: Float)

/**
 * Равномерно распределить и заполнить пространство квадратами с одинаковой высотой по горизонтальной оси
 */
fun fitSquaresHorizontally(quads: List<Size>, width: Float, height: Float): List<Rect2> {
    val totalWidth = quads.sumOf { it.width }
    val freeX = (width - totalWidth).coerceAtLeast(0f)
    val appendPerCellX = freeX / quads.size

    var x = 0f
    return quads.map { quad ->
        val w = quad.width + appendPerCellX
        val rect = Rect2(x, 0f, x + w, height)
        x += w
        rect
    }
}

fun <T> List<T>.fitSquaresHorizontally(width: Float, height: Float, quad: (T) -> Size): List<Pair<T, Rect2>> {
    val quads = map(quad)
    return fitSquaresHorizontally(quads, width, height).mapIndexed { index, rect -> this[index] to rect }
}