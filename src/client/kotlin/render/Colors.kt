package org.lain.engine.client.render

const val BLACK_TRANSPARENT = 0x80000000.toInt()
const val PURPLE_TRANSPARENT = 0x80800080.toInt()
const val WHITE = 0xFFFFFFFF.toInt()
const val BLUE = 0xFF5FC8FF.toInt()
const val BLACK = 0x00000000
const val ORANGE = 16743232
const val AQUA = 4259767
const val GRAY = 0xAAAAAA
const val DARK_RED = 0xAA0000
const val YELLOW = 0xFFFF55

const val DEFAULT_TEXT_COLOR = WHITE
const val DEV_MODE_TEXT_COLOR = BLUE

const val SPECTATOR_MODE_TEXT_COLOR = BLUE
const val WARNING_COLOR = ORANGE

const val STAMINA_COLOR = ORANGE
const val SPEED_COLOR = AQUA

const val SPY_COLOR = ORANGE
const val HIGH_VOLUME_COLOR = DARK_RED
const val LOW_VOLUME_COLOR = YELLOW

fun whiteWithAlpha(a: Int) = WHITE.withAlpha(a)

fun Int.withDarken(factor: Float): Int {
    val a = (this shr 24) and 0xFF
    val r = ((this shr 16) and 0xFF)
    val g = ((this shr 8) and 0xFF)
    val b = (this and 0xFF)

    val newR = (r * factor).toInt().coerceIn(0, 255)
    val newG = (g * factor).toInt().coerceIn(0, 255)
    val newB = (b * factor).toInt().coerceIn(0, 255)

    return (a shl 24) or (newR shl 16) or (newG shl 8) or newB
}

fun Int.blend(
    other: Int,
    colorA: Float,
    alphaA: Float = 0f
): Int {
    val cA = colorA.coerceIn(0f, 1f)
    val aA = alphaA.coerceIn(0f, 1f)

    val a1 = (this ushr 24) and 0xFF
    val r1 = (this ushr 16) and 0xFF
    val g1 = (this ushr 8) and 0xFF
    val b1 = this and 0xFF

    val a2 = (other ushr 24) and 0xFF
    val r2 = (other ushr 16) and 0xFF
    val g2 = (other ushr 8) and 0xFF
    val b2 = other and 0xFF

    val outR = (r1 * (1f - cA) + r2 * cA).toInt().coerceIn(0, 255)
    val outG = (g1 * (1f - cA) + g2 * cA).toInt().coerceIn(0, 255)
    val outB = (b1 * (1f - cA) + b2 * cA).toInt().coerceIn(0, 255)

    val outA = (a1 * (1f - aA) + a2 * aA).toInt().coerceIn(0, 255)

    return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
}

fun Int.multiplyColor(
    r: Float = 1f,
    g: Float = 1f,
    b: Float = 1f,
    alpha: Float = 1f
): Int {
    val a1 = (this ushr 24) and 0xFF
    val r1 = (this ushr 16) and 0xFF
    val g1 = (this ushr 8) and 0xFF
    val b1 = this and 0xFF

    val f = alpha.coerceIn(0f, 1f)

    val outR = (r1 * (1f - f) + (r1 * r).coerceIn(0f, 255f) * f).toInt().coerceIn(0, 255)
    val outG = (g1 * (1f - f) + (g1 * g).coerceIn(0f, 255f) * f).toInt().coerceIn(0, 255)
    val outB = (b1 * (1f - f) + (b1 * b).coerceIn(0f, 255f) * f).toInt().coerceIn(0, 255)
    val outA = (a1 * (1f - f) + a1 * f).toInt().coerceIn(0, 255) // альфа подмешивается пропорционально alpha

    return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
}


fun Int.withAlpha(t: Float): Int {
    val a = ((t.coerceIn(0f, 1f) * 255).toInt() shl 24)
    return a or (this and 0x00FFFFFF)
}

fun Int.withAlpha(alpha: Int): Int {
    return ((alpha and 0xFF) shl 24) or (this and 0x00FFFFFF)
}