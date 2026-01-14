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
        // FIXME: Нейрокод. Я заебался.
        is Layout.Linear -> {
            val mainAxisForward = layout.placement == Placement.POSITIVE
            var cursor = if (mainAxisForward) 0f else when(layout.axis) {
                Axis.VERTICAL -> data.measuredSize.height
                Axis.HORIZONTAL -> data.measuredSize.width
            }

            composition.children.forEach { child ->
                val measuredSize = child.measuredLayout.measuredSize
                val x: Float
                val y: Float

                if (layout.axis == Axis.HORIZONTAL) {
                    x = cursor
                    y = 0f

                    cursor += if (mainAxisForward) measuredSize.width + layout.gap
                    else -(measuredSize.width + layout.gap)
                } else {
                    x = 0f
                    y = cursor

                    cursor += if (mainAxisForward) measuredSize.height + layout.gap
                    else -(measuredSize.height + layout.gap)
                }

                layout(child, Vec2(x, y))
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