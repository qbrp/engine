package org.lain.engine.client.render.ui

import org.lain.engine.client.render.Vec2

data class PositionedLayout(
    val size: Size,
    val position: Vec2,
    val origin: Vec2
)

fun layout(
    composition: Composition,
    position: Vec2 = Vec2(0f, 0f)
) {
    val fragment = composition.fragment
    val data = composition.measuredLayout

    // Финальная позиция этого фрагмента в глобальных координатах
    val finalPos = position.add(fragment.position)

    when (val layout = fragment.layout) {

        is Layout.Horizontal -> {
            var currentX = 0f
            for (child in composition.children) {
                val childPos = Vec2(currentX, 0f)
                layout(child, childPos)
                currentX += child.measuredLayout.measuredSize.width + layout.gap
            }
        }

        is Layout.Vertical -> {
            var currentY = 0f
            for (child in composition.children) {
                val childPos = Vec2(0f, currentY)
                layout(child, childPos)
                currentY += child.measuredLayout.measuredSize.height + layout.gap
            }
        }

        is Layout.Absolute -> {
            for (child in composition.children) {
                layout(child, child.fragment.position)
            }
        }

        null -> {}
    }

    val origin = Vec2(
        fragment.pivot.x * data.measuredSize.width,
        fragment.pivot.y * data.measuredSize.height
    )

    composition.positionedLayout = PositionedLayout(
        size = data.measuredSize,
        position = finalPos,
        origin = origin,
    )
}