package org.lain.engine.client.render.ui

import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.MutableVec2
import org.lain.engine.client.render.Vec2
import org.lain.engine.client.render.ZeroMutableVec2
import org.lain.engine.util.Color
import org.lain.engine.util.text.EngineOrderedTextSequence
import kotlin.math.max

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

typealias RenderListener = (UiState) -> Unit
typealias MeasureListener = (Composition) -> Unit
typealias HoverListener = (UiState, Float, Float) -> Unit
typealias ClickListener = (UiState, Float, Float) -> Boolean

data class TextState(
    val lines: List<EngineOrderedTextSequence>,
    val color: Color = Color.WHITE,
    val scale: Float = 1f
)

data class Background(
    val color1: Color = Color.WHITE.withAlpha(0),
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

data class UiListeners(
    var click: ClickListener? = null,
    var hover: HoverListener? = null,
    var render: RenderListener? = null
)

data class UiState(
    val position: MutableVec2 = ZeroMutableVec2(),
    val origin: MutableVec2 = ZeroMutableVec2(),
    val size: MutableSize = MutableSize(0f, 0f),
    val scale: MutableVec2 = DEFAULT_SCALE,
    val features: UiFeatures = UiFeatures(),
    val listeners: UiListeners = UiListeners(),
    var visible: Boolean = true
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
    fun addFragment(constraints: Size? = null, clear: Boolean = true, fragment: () -> Fragment): Composition
    fun removeComposition(composition: Composition)
}
