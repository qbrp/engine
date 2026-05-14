package org.lain.engine.client.render.ui

import net.minecraft.util.FormattedCharSequence
import org.lain.engine.client.render.EngineSprite
import org.lain.engine.player.PlayerId
import org.lain.engine.util.Color
import org.lain.engine.util.math.MutableVec2
import org.lain.engine.util.math.Vec2
import org.lain.engine.util.math.ZeroMutableVec2
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

fun Size(width: Float = 0f, height: Float = 0f): Size = MutableSize(width, height)

data class MutableSize(override var width: Float = 0f, override var height: Float = 0f) : Size {
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
typealias RecomposeListener = (Composition) -> Unit
typealias HoverListener = (UiState, Float, Float) -> Unit
typealias ClickListener = (UiState, Float, Float) -> InputResult
typealias KeyListener = (UiState, KeyEvent) -> Unit
typealias CharListener = (UiState, CharEvent) -> Unit

data class KeyEvent(val key: Int, val action: KeyAction, val modifiers: Set<Modifier>)

data class CharEvent(val code: Int, val modifiers: Set<Modifier>)

// TODO: Запилить поддержку REPEAT в будущем
enum class KeyAction {
    PRESS, RELEASE
}

enum class Modifier {
    SHIFT, CTRL, ALT, SUPER, CAPS_LOCK, NUM_LOCK
}

enum class InputResult {
    CONTINUE, FINISH
}

data class TextState(
    val lines: List<FormattedCharSequence>,
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
    var text: TextState? = null,
    var head: PlayerId? = null
)

data class UiListeners(
    var click: ClickListener? = null,
    var hover: HoverListener? = null,
    var render: RenderListener? = null,
    var key: KeyListener? = null,
    var char: CharListener? = null
)

data class UiState(
    val position: MutableVec2 = ZeroMutableVec2(),
    val origin: MutableVec2 = ZeroMutableVec2(),
    val size: MutableSize = MutableSize(0f, 0f),
    val scale: MutableVec2 = DEFAULT_SCALE,
    val features: UiFeatures = UiFeatures(),
    val listeners: UiListeners = UiListeners(),
    var borders: LineBorders = LineBorders(),
    var visible: Boolean = true,
    var opacity: Int = 255,
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

data class UiElement(val composition: Composition, val clear: Boolean)

interface EngineUi {
    val elements: List<UiElement>
    /**
     * @param clear Удалять ли фрагмент при очищении экрана (например, при выходе из игры)
     * @param focus Вызвать ли `focusAppropriateElement` при создании элемента
     */
    fun addFragment(clear: Boolean = true, focus: Boolean = false, fragment: () -> Fragment): Composition
    fun removeComposition(composition: Composition)
}