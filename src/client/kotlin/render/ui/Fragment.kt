package org.lain.engine.client.render.ui

import org.lain.engine.client.render.MutableVec2
import org.lain.engine.client.render.ZeroMutableVec2
import org.lain.engine.util.sumOf
import kotlin.math.max
import kotlin.math.min

data class MeasuredLayout(val measuredSize: Size)

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

fun measure(
    context: UiContext,
    composition: Composition,
    constraints: Size = context.windowSize
) {
    // Определяем внутренние constraints для детей
    val fragment = composition.fragment
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
        composition.measuredLayout = MeasuredLayout(intrinsicSize)
        return
    }

    // 4. Container element: есть layout, измеряем детей
    composition.children.forEach { measure(context, it, innerConstraints) }
    val childrenData = composition.children.map { it.measuredLayout }
    var totalWidth: Float = intrinsicSize.width
    var totalHeight: Float = intrinsicSize.height

    when (layout) {
        is Layout.Linear -> {
            val mainSum: Float
            val crossMax: Float

            if (layout.axis == Axis.HORIZONTAL) {
                mainSum = childrenData.sumOf { it.measuredSize.width } +
                        max(0, childrenData.size - 1) * layout.gap
                crossMax = childrenData.maxOfOrNull { it.measuredSize.height } ?: 0f

                totalWidth = max(totalWidth, mainSum)
                totalHeight = max(totalHeight, crossMax)
            } else {
                mainSum = childrenData.sumOf { it.measuredSize.height } +
                        max(0, childrenData.size - 1) * layout.gap
                crossMax = childrenData.maxOfOrNull { it.measuredSize.width } ?: 0f

                totalWidth = max(totalWidth, crossMax)
                totalHeight = max(totalHeight, mainSum)
            }
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

    composition.measuredLayout = MeasuredLayout(Size(totalWidth, totalHeight))
}