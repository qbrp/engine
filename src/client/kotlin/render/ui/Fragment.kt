package org.lain.engine.client.render.ui

import org.lain.engine.client.render.EngineSprite
import org.lain.engine.util.Vec2
import org.lain.engine.client.render.ui.UiState.Companion.DEFAULT_SCALE
import org.lain.engine.util.Color
import org.lain.engine.util.text.EngineText

data class Pivot(
    val x: Float,
    val y: Float
) {
    companion object {
        val TOP_LEFT = Pivot(0f, 0f)
        val BOTTOM_LEFT = Pivot(0f, 1f)
        val TOP_RIGHT = Pivot(1f, 0f)
    }
}

sealed class Layout {
    data class Linear(val gap: Float, val axis: Axis, val placement: Placement) : Layout()
    object Absolute : Layout()
}

enum class Axis {
    VERTICAL, HORIZONTAL
}

enum class Placement {
    POSITIVE, NEGATIVE
}

fun HorizontalLayout(gap: Float, placement: Placement = Placement.POSITIVE) = Layout.Linear(gap, Axis.HORIZONTAL, placement)

fun VerticalLayout(gap: Float, placement: Placement = Placement.POSITIVE) = Layout.Linear(gap, Axis.VERTICAL, placement)

sealed class ConstraintsSize {
    data class Fixed(val fixed: Float) : ConstraintsSize()
    object Wrap : ConstraintsSize()
    object MatchParent : ConstraintsSize()
}

data class Sizing(
    val width: ConstraintsSize,
    val height: ConstraintsSize
) {
    constructor(s: ConstraintsSize) : this(s, s)
    constructor(width: Float, height: Float) : this(ConstraintsSize.Fixed(width), ConstraintsSize.Fixed(height))

    companion object {
        fun Wrap() = Sizing(ConstraintsSize.Wrap)
        fun MatchParent() = Sizing(ConstraintsSize.MatchParent)
    }
}

typealias Padding = FloatBorders

data class FloatBorders(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f
) {
    constructor(value: Float): this(value, value, value, value)
    constructor(horizontal: Float, vertical: Float): this(vertical, horizontal, vertical, horizontal)

    fun horizontal() = left + right
    fun vertical() = top + bottom
}

data class LineBorders(
    val top: Line? = null,
    val right: Line? = null,
    val bottom: Line? = null,
    val left: Line? = null
) {
    constructor(line: Line): this(line, line, line, line)
    constructor(color: Color, thickness: Float): this(Line(color, thickness))
}

data class Line(
    val color: Color,
    val thickness: Float,
)

data class TextInput(
    val multiline: Boolean,
    val onValueChanged: TextInputCallback
)

typealias TextInputCallback = (List<String>) -> Unit

data class Fragment(
    val position: Vec2 = Vec2(0f, 0f),
    val layout: Layout? = null,
    val sizing: Sizing = Sizing(ConstraintsSize.Wrap),
    val padding: FloatBorders = FloatBorders(),
    val borders: LineBorders = LineBorders(),
    val pivot: Pivot = Pivot.TOP_LEFT,
    val scale: Vec2 = DEFAULT_SCALE,
    val children: List<Fragment> = listOf(),
    val text: TextArea? = null,
    val image: Image? = null,
    val background: Background? = null,
    val onClick: ClickListener? = null,
    val onRender: RenderListener? = null,
    val onRecompose: RecomposeListener? = null,
    val onHover: HoverListener? = null,
    val textInput: TextInput? = null
) {

    override fun hashCode(): Int {
        var result = position.hashCode()
        result = 31 * result + (layout?.hashCode() ?: 0)
        result = 31 * result + sizing.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + borders.hashCode()
        result = 31 * result + pivot.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + children.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (image?.hashCode() ?: 0)
        result = 31 * result + (background?.hashCode() ?: 0)
        result = 31 * result + (onClick?.hashCode() ?: 0)
        result = 31 * result + (onRender?.hashCode() ?: 0)
        result = 31 * result + (onRecompose?.hashCode() ?: 0)
        result = 31 * result + (onHover?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fragment

        if (position != other.position) return false
        if (layout != other.layout) return false
        if (sizing != other.sizing) return false
        if (padding != other.padding) return false
        if (borders != other.borders) return false
        if (pivot != other.pivot) return false
        if (scale != other.scale) return false

        for (child in children) {
            for (otherChildren in other.children) {
                if (child != otherChildren) return false
            }
        }

        if (text != other.text) return false
        if (image != other.image) return false
        if (background != other.background) return false

        return true
    }
}

data class TextArea(
    val content: EngineText,
    val scale: Float = 1f
)

data class Image(
    val sprite: EngineSprite,
    val sizing: SpriteSizing
)

sealed class SpriteSizing {
    data class Fixed(val size: Size) : SpriteSizing()
    object Stretch : SpriteSizing()
}