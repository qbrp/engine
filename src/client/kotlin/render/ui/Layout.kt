package org.lain.engine.client.render.ui

import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.MutableVec2
import org.lain.engine.client.render.Vec2
import org.lain.engine.client.render.ui.UiElementState.Companion.DEFAULT_SCALE
import org.lain.engine.util.EngineText
import kotlin.math.max
import kotlin.math.min

data class Pivot(
    val x: Float,
    val y: Float
) {
    companion object {
        val TOP_LEFT = Pivot(0f, 0f)
    }
}

sealed class Layout {
    data class Horizontal(val gap: Float) : Layout()
    data class Vertical(val gap: Float) : Layout()
    object Absolute : Layout()
}

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
    val background: Background? = null
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
    val fontRenderer: FontRenderer
)

fun resolveSize(context: UiContext, fragment: Fragment, constraints: Size): Size {
    val totalSize = MutableSize(0f, 0f)
    when (val spriteSizing = fragment.image?.sizing) {
        is SpriteSizing.Fixed -> {
            val size = spriteSizing.size
            totalSize.stretch(size.width, size.height)
        }
        SpriteSizing.Stretch -> {
            totalSize.stretch(constraints.width, constraints.height)
        }
        null -> {}
    }

    fragment.text?.let { text ->
        val lines = context.fontRenderer.breakTextByLines(text.content, constraints.width / text.scale)
        totalSize.stretch(constraints.width, context.fontRenderer.fontHeight * lines.count())
    }
    return totalSize
}

data class LayoutData(
    val fragment: Fragment,
    val measuredSize: Size,
    val children: List<LayoutData> = emptyList()
)

fun constraintsAxis(size: ConstraintsSize, constraint: Float): Float {
    return when (size) {
        is ConstraintsSize.Fixed -> size.fixed.coerceAtMost(constraint)
        is ConstraintsSize.Wrap, is ConstraintsSize.MatchParent -> constraint
    }
}

fun leafSizeAxis(sizing: ConstraintsSize, intrinsic: Float, constraint: Float): Float {
    return when (sizing) {
        is ConstraintsSize.Fixed -> sizing.fixed
        is ConstraintsSize.Wrap -> min(intrinsic, constraint)
        is ConstraintsSize.MatchParent -> constraint
    }
}

fun measure(context: UiContext, fragment: Fragment, constraints: Size): LayoutData {
    // Определяем внутренние constraints для детей
    val sizing = fragment.sizing
    val innerConstraints = Size(
        constraintsAxis(sizing.width, constraints.width),
        constraintsAxis(sizing.height, constraints.height)
    )

    val intrinsicSize = resolveSize(context, fragment, constraints).let {
        Size(
            leafSizeAxis(sizing.width, it.width, constraints.width),
            leafSizeAxis(sizing.height, it.height, constraints.height)
        )
    }

    // 3. Leaf element: нет layout → возвращаем intrinsic или Fixed
    val layout = fragment.layout
    if (layout == null) {
        return LayoutData(fragment, intrinsicSize, listOf())
    }

    // 4. Container element: есть layout, измеряем детей
    val childrenData = fragment.children.map { measure(context, it, innerConstraints) }
    var totalWidth: Float = intrinsicSize.width
    var totalHeight: Float = intrinsicSize.height
    val gap = when (val layout = fragment.layout) {
        is Layout.Horizontal -> layout.gap
        is Layout.Vertical -> layout.gap
        else -> 0f
    }

    when (layout) {
        is Layout.Horizontal -> {
            val maxHeight = childrenData.maxOfOrNull { it.measuredSize.height } ?: 0f
            totalWidth = max(totalWidth, childrenData.sumOf { it.measuredSize.width.toDouble() }.toFloat())
            if (childrenData.size > 1) totalWidth += gap * (childrenData.size - 1)
            totalHeight = max(maxHeight, intrinsicSize.height)
        }

        is Layout.Vertical -> {
            val maxWidth = childrenData.maxOfOrNull { it.measuredSize.width } ?: 0f
            totalHeight = max(totalHeight, childrenData.sumOf { it.measuredSize.height.toDouble() }.toFloat())
            if (childrenData.size > 1) totalHeight += gap * (childrenData.size - 1)
            totalWidth = max(maxWidth, intrinsicSize.width)
        }

        is Layout.Absolute -> {
            // Bounding box по детям
            val maxX = childrenData.maxOfOrNull { it.measuredSize.width } ?: 0f
            val maxY = childrenData.maxOfOrNull { it.measuredSize.height } ?: 0f
            totalWidth = max(intrinsicSize.width, maxX)
            totalHeight = max(intrinsicSize.height, maxY)
        }
    }

    // 5. Clamp по constraints
    totalWidth = min(totalWidth, constraints.width)
    totalHeight = min(totalHeight, constraints.height)

    return LayoutData(fragment, Size(totalWidth, totalHeight), childrenData)
}

data class PositionedFragment(
    val fragment: Fragment,
    val size: Size,
    val position: Vec2,
    val origin: Vec2,
    val children: List<PositionedFragment> = emptyList()
)

fun layout(
    data: LayoutData,
    position: Vec2 = Vec2(0f, 0f)
): PositionedFragment {
    val childrenResult = mutableListOf<PositionedFragment>()

    // Финальная позиция этого фрагмента в глобальных координатах
    val finalPos = position.add(data.fragment.position)

    when (val layout = data.fragment.layout) {

        is Layout.Horizontal -> {
            var currentX = 0f
            for (child in data.children) {
                val childPos = Vec2(currentX, 0f)
                childrenResult += layout(child, childPos)
                currentX += child.measuredSize.width + layout.gap
            }
        }

        is Layout.Vertical -> {
            var currentY = 0f
            for (child in data.children) {
                val childPos = Vec2(0f, currentY)
                childrenResult += layout(child, childPos)
                currentY += child.measuredSize.height + layout.gap
            }
        }

        is Layout.Absolute -> {
            for (child in data.children) {
                childrenResult += layout(
                    child,
                    child.fragment.position
                )
            }
        }

        null -> {}
    }

    val origin = Vec2(
        data.fragment.pivot.x * data.measuredSize.width,
        data.fragment.pivot.y * data.measuredSize.height
    )

    return PositionedFragment(
        fragment = data.fragment,
        size = data.measuredSize,
        position = finalPos,
        origin = origin,
        children = childrenResult
    )
}


fun fragmentsToUiElements(context: UiContext, node: PositionedFragment): UiElementState {
    val fragment = node.fragment
    return UiElementState(
        MutableVec2(node.position),
        MutableVec2(node.origin),
        MutableSize(node.size.width, node.size.height),
        children = node.children.map { fragmentsToUiElements(context, it) }.toMutableList(),
        features = UiFeatures(
            background = fragment.background ?: Background(),
            sprite = fragment.image?.let { image ->
                UiSprite(
                    image.sprite,
                    when(val s = image.sizing) {
                        is SpriteSizing.Fixed -> MutableSize(s.size)
                        is SpriteSizing.Stretch -> MutableSize(node.size)
                    }
                )
            },
            text = fragment.text?.let { text ->
                TextState(
                    context.fontRenderer.breakTextByLines(text.content, node.size.width / text.scale),
                    Color.WHITE,
                    text.scale
                )
            }
        )
    )
}