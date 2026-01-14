package org.lain.engine.client.render.ui

import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.Vec2
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
}

data class Fragment(
    val position: Vec2 = Vec2(0f, 0f),
    val layout: Layout? = null,
    val sizing: Sizing = Sizing(ConstraintsSize.Wrap),
    val pivot: Pivot = Pivot.TOP_LEFT,
    val scale: Vec2 = DEFAULT_SCALE,
    val children: List<Fragment> = mutableListOf(),
    val text: TextArea? = null,
    val image: Image? = null,
    val background: Background? = null,
    val onClick: ClickListener? = null,
    val onRender: RenderListener? = null,
    val onRecompose: RecomposeListener? = null,
    val onHover: HoverListener? = null,
)

data class TextArea(
    val content: EngineText,
    val scale: Float = 1f
)

data class Image(
    val sprite: EngineSprite,
    val sizing: SpriteSizing
)

sealed class SpriteSizing {
    class Fixed(val size: Size) : SpriteSizing()
    object Stretch : SpriteSizing()
}

data class UiContext(
    val fontRenderer: FontRenderer,
    val windowSize: Size
)

fun updateCompositionUiState(
    composition: Composition,
    context: UiContext
) = composition.render.apply {
    val fragment = composition.fragment
    val layout = composition.positionedLayout
    position.set(layout.position)
    origin.set(layout.origin)

    size.width = layout.size.width
    size.height = layout.size.height

    features.background = fragment.background ?: Background()

    features.sprite = fragment.image?.let { image ->
        val spriteSize = when (val s = image.sizing) {
            is SpriteSizing.Fixed -> MutableSize(s.size)
            is SpriteSizing.Stretch -> MutableSize(layout.size)
        }
        UiSprite(image.sprite, spriteSize)
    }

    features.text = fragment.text?.let { text ->
        TextState(
            context.fontRenderer.breakTextByLines(
                text.content,
                layout.size.width / text.scale
            ),
            Color.WHITE,
            text.scale
        )
    }

    listeners.apply {
        click = fragment.onClick
        render = fragment.onRender
        hover = fragment.onHover
    }

    update()
}
