package org.lain.engine.client.render.ui

import org.lain.engine.util.Vec2

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
    val padding = fragment.padding
    val data = composition.measuredLayout

    // Финальная позиция этого фрагмента в глобальных координатах
    val finalPos = position.add(fragment.position)

    when (val layout = fragment.layout) {
        is Layout.Linear -> {
            val forward = layout.placement == Placement.POSITIVE

            var relX = if (!forward && layout.axis == Axis.HORIZONTAL) data.measuredSize.width - padding.right else padding.left
            var relY = if (!forward && layout.axis == Axis.VERTICAL) data.measuredSize.height - padding.bottom else padding.top

            composition.children.forEach { child ->
                val measuredSize = child.measuredLayout.measuredSize
                layout(child, Vec2(relX, relY))

                if (layout.axis == Axis.HORIZONTAL) {
                    val append = measuredSize.width + layout.gap
                    relX = if (forward) relX + append else relX - append
                }

                if (layout.axis == Axis.VERTICAL) {
                    val append = measuredSize.height + layout.gap
                    relY = if (forward) relY + append else relY - append
                }
            }
        }


        is Layout.Absolute -> {
            for (child in composition.children) {
                layout(
                    child,
                    child.fragment.position.add(
                        padding.left - padding.right,
                        padding.vertical()
                    )
                )
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