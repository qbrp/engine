package org.lain.engine.client.render.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.Window
import net.minecraft.text.OrderedText
import org.joml.Matrix3f
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.lain.engine.client.mc.drawEngineSprite
import org.lain.engine.client.mc.fill
import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.MutableVec2
import org.lain.engine.client.render.Vec2
import org.lain.engine.client.render.blend
import org.lain.engine.util.EngineOrderedTextSequence
import org.lain.engine.util.EngineText
import org.lain.engine.util.MutableVec3
import org.lain.engine.util.Vec3
import org.lain.engine.util.toMinecraft
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

interface Size {
    val width: Float
    val height: Float

    val centerX get() = width / 2
    val centerY get() = height / 2
    val center get() = Vec2(centerX, centerY)
}

fun clampedSize(
    size: Size,
    x0: Float? = null, y0: Float? = null,
    x1: Float? = null, y1: Float? = null
): Size {
    val mutable = MutableSize(size.width, size.height)
    if (x0 != null && y0 != null) {
        mutable.clampMin(x0, y0)
    }
    if (x1 != null && y1 != null) {
        mutable.clampMax(x1, y1)
    }
    return mutable
}

fun Size(width: Float, height: Float): Size = MutableSize(width, height)

data class MutableSize(override var width: Float, override var height: Float) : Size {
    constructor(size: Size) : this(size.width, size.height)

    fun clampMin(x: Float, y: Float) {
        width = width.coerceAtLeast(x)
        height = height.coerceAtLeast(y)
    }

    fun clampMax(x: Float, y: Float) {
        width = width.coerceAtMost(x)
        height = height.coerceAtMost(y)
    }
    fun stretch(x: Float = width, y: Float = height) {
        width = max(width, x)
        height = max(height, y)
    }
}

@JvmInline
value class Color(val integer: Int) {
    constructor(long: Long) : this(long.toInt())

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

    companion object {
        val WHITE_TRANSPARENT = Color(0x00FFFFFF)
        val WHITE = Color(0xFFFFFFFF)
        val BLACK_TRANSPARENT = Color(org.lain.engine.client.render.BLACK_TRANSPARENT)
    }
}

data class TextState(
    val lines: List<EngineOrderedTextSequence>,
    val color: Color = Color.WHITE,
    val scale: Float = 1f
)

data class Background(
    val color1: Color = Color.WHITE_TRANSPARENT,
    val color2: Color = color1
)

data class Tint(
    val color1: Color,
    val colorA: Float,
    val alphaA: Float = colorA
) {
    fun blend(color: Color): Color {
        return color.blend(color1, colorA, alphaA)
    }
}

fun Tint?.blend(color: Color): Color {
    return this?.blend(color) ?: color
}

data class UiSprite(
    val source: EngineSprite,
    val size: MutableSize,
    val color: Color = Color.WHITE
)

data class UiFeatures(
    var background: Background = Background(),
    var sprite: UiSprite? = null,
    var tint: Tint? = null,
    var text: TextState? = null
)

data class UiElementState(
    val position: MutableVec2,
    val origin: MutableVec2,
    val size: MutableSize,
    val scale: MutableVec2 = DEFAULT_SCALE,
    val children: MutableList<UiElementState> = mutableListOf(),
    val features: UiFeatures,
) {
    val scaledSize = MutableSize(size.width, size.height)
    fun update() {
        scaledSize.width = size.width * scale.x
        scaledSize.height = size.height * scale.y
    }

    companion object {
        val DEFAULT_SCALE = MutableVec2(1f, 1f)
        val DEFAULT_ORIGIN = MutableVec2(0f, 0f)
    }
}

interface EngineUi {
    fun addRootFragment(fragment: Fragment): UiElementState
    fun addRootElement(state: UiElementState)
    fun removeRootElement(state: UiElementState)
}
