package org.lain.engine.util

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class Color(val integer: Int) {
    constructor(long: Long) : this(long.toInt())

    val alpha
        get() = integer ushr 24 and 0xFF

    fun blend(
        other: Color?,
        colorA: Float?,
        alphaA: Float = 0f
    ): Color {
        if (other == null || colorA == null) return this
        val cA = colorA.coerceIn(0f, 1f)
        val aA = alphaA.coerceIn(0f, 1f)

        val a1 = (integer ushr 24) and 0xFF
        val r1 = (integer ushr 16) and 0xFF
        val g1 = (integer ushr 8) and 0xFF
        val b1 = integer and 0xFF

        val other = other.integer
        val a2 = (other ushr 24) and 0xFF
        val r2 = (other ushr 16) and 0xFF
        val g2 = (other ushr 8) and 0xFF
        val b2 = other and 0xFF

        val outR = (r1 * (1f - cA) + r2 * cA).toInt().coerceIn(0, 255)
        val outG = (g1 * (1f - cA) + g2 * cA).toInt().coerceIn(0, 255)
        val outB = (b1 * (1f - cA) + b2 * cA).toInt().coerceIn(0, 255)

        val outA = (a1 * (1f - aA) + a2 * aA).toInt().coerceIn(0, 255)

        return Color((outA shl 24) or (outR shl 16) or (outG shl 8) or outB)
    }

    fun withDarken(factor: Float): Color {
        val a = (integer shr 24) and 0xFF
        val r = ((integer shr 16) and 0xFF)
        val g = ((integer shr 8) and 0xFF)
        val b = (integer and 0xFF)

        val newR = (r * factor).toInt().coerceIn(0, 255)
        val newG = (g * factor).toInt().coerceIn(0, 255)
        val newB = (b * factor).toInt().coerceIn(0, 255)

        return Color((a shl 24) or (newR shl 16) or (newG shl 8) or newB)
    }

    fun blend(
        other: Int,
        colorA: Float,
        alphaA: Float = 0f
    ): Color {
        val cA = colorA.coerceIn(0f, 1f)
        val aA = alphaA.coerceIn(0f, 1f)

        val a1 = (integer ushr 24) and 0xFF
        val r1 = (integer ushr 16) and 0xFF
        val g1 = (integer ushr 8) and 0xFF
        val b1 = integer and 0xFF

        val a2 = (other ushr 24) and 0xFF
        val r2 = (other ushr 16) and 0xFF
        val g2 = (other ushr 8) and 0xFF
        val b2 = other and 0xFF

        val outR = (r1 * (1f - cA) + r2 * cA).toInt().coerceIn(0, 255)
        val outG = (g1 * (1f - cA) + g2 * cA).toInt().coerceIn(0, 255)
        val outB = (b1 * (1f - cA) + b2 * cA).toInt().coerceIn(0, 255)

        val outA = (a1 * (1f - aA) + a2 * aA).toInt().coerceIn(0, 255)

        return Color((outA shl 24) or (outR shl 16) or (outG shl 8) or outB)
    }

    fun withAlpha(alpha: Int): Color {
        return Color(((alpha and 0xFF) shl 24) or (integer and 0x00FFFFFF))
    }

    fun multiply(
        r: Float = 1f,
        g: Float = 1f,
        b: Float = 1f,
        alpha: Float = 1f
    ): Int {
        val a1 = (integer ushr 24) and 0xFF
        val r1 = (integer ushr 16) and 0xFF
        val g1 = (integer ushr 8) and 0xFF
        val b1 = integer and 0xFF

        val f = alpha.coerceIn(0f, 1f)

        val outR = (r1 * (1f - f) + (r1 * r).coerceIn(0f, 255f) * f).toInt().coerceIn(0, 255)
        val outG = (g1 * (1f - f) + (g1 * g).coerceIn(0f, 255f) * f).toInt().coerceIn(0, 255)
        val outB = (b1 * (1f - f) + (b1 * b).coerceIn(0f, 255f) * f).toInt().coerceIn(0, 255)
        val outA = (a1 * (1f - f) + a1 * f).toInt().coerceIn(0, 255) // альфа подмешивается пропорционально alpha

        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }


    companion object {
        val BASE_TRANSPARENCY = 165
        val WHITE = Color(0xFFFFFFFF)
        val BLACK = Color(0xFF000000)
        val BLUE = Color(0xFF5FC8FF)
        val ORANGE = Color(0xFFFFA000)
        val AQUA = Color(0xFF40E0D0)
        val GRAY = Color(0xFFAAAAAA)
        val DARK_RED = Color(0xFFAA0000)
        val YELLOW = Color(0xFFFFFF55)
        val RED = Color(0xFFFF0000)

        fun parseString(string: String): Color {
            return Color(Integer.parseInt(string, 16))
        }
    }
}

// Themes

val BLACK_TRANSPARENT_BG_COLOR = Color.BLACK.withAlpha(Color.BASE_TRANSPARENCY)

val DEFAULT_TEXT_COLOR = Color.WHITE
val DEV_MODE_COLOR = Color.BLUE

val SPECTATOR_MODE_COLOR = Color.BLUE
val WARNING_COLOR = Color.ORANGE
val FREECAM_WARNING_COLOR = Color.RED

val STAMINA_COLOR = Color.ORANGE
val SPEED_COLOR = Color.AQUA

val SPY_COLOR = Color.ORANGE
val HIGH_VOLUME_COLOR = Color.DARK_RED
val LOW_VOLUME_COLOR = Color.YELLOW

val BLOCK_HINT_COLOR = Color.ORANGE