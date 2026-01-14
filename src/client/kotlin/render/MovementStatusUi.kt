package org.lain.engine.client.render

import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.Background
import org.lain.engine.client.render.ui.ConstraintsSize
import org.lain.engine.client.render.ui.Fragment
import org.lain.engine.client.render.ui.Layout
import org.lain.engine.client.render.ui.Pivot
import org.lain.engine.client.render.ui.Placement
import org.lain.engine.client.render.ui.Sizing
import org.lain.engine.client.render.ui.VerticalLayout
import org.lain.engine.util.BLACK_TRANSPARENT_BG_COLOR
import org.lain.engine.util.Color
import org.lain.engine.util.SPEED_COLOR
import org.lain.engine.util.STAMINA_COLOR
import org.lain.engine.util.lerp
import kotlin.math.abs

fun MovementBar(gameSession: GameSession): Fragment {
    val renderer = gameSession.renderer
    fun Bar(color: Color, color2: Color = color, supplier: () -> Float): Fragment {
        var width = 0f

        return Fragment(
            layout = Layout.Absolute,
            background = Background(Color.BLACK),
            sizing = Sizing(
                ConstraintsSize.MatchParent,
                ConstraintsSize.Fixed(2f)
            ),
            children = listOf(
                Fragment(
                    background = Background(color, color2),
                    sizing = Sizing(ConstraintsSize.MatchParent),
                    onRender = { state ->
                        val size = state.size
                        val value = supplier()
                        size.width = width * value
                        state.visible = !renderer.hudHidden && renderer.isFirstPerson
                    },
                    onRecompose = { composition -> width = composition.render.size.width }
                ),
            ),
        )
    }

    return Fragment(
        position = Vec2(2f, gameSession.client.window.heightDp - 3),
        sizing = Sizing(
            ConstraintsSize.Fixed(64f),
            ConstraintsSize.Wrap
        ),
        layout = VerticalLayout(1f, Placement.NEGATIVE),
        children = listOf(
            Bar(SPEED_COLOR) { gameSession.movementManager.intention },
            Bar(STAMINA_COLOR) { gameSession.movementManager.stamina },
        ),
        pivot = Pivot.BOTTOM_LEFT
    )
}