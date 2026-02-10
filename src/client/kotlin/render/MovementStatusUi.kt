package org.lain.engine.client.render

import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.*
import org.lain.engine.player.isSpectating
import org.lain.engine.util.Color
import org.lain.engine.util.SPEED_COLOR
import org.lain.engine.util.STAMINA_COLOR
import org.lain.engine.util.math.Vec2
import org.lain.engine.util.math.lerp

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
                        size.width = lerp(size.width, width * value, 0.5f)
                    },
                    onRecompose = { composition -> width = composition.render.size.width }
                ),
            ),
        )
    }

    return Fragment(
        position = Vec2(2f, gameSession.client.window.heightDp - 3f),
        sizing = Sizing(
            ConstraintsSize.Fixed(64f),
            ConstraintsSize.Wrap
        ),
        layout = VerticalLayout(1f, Placement.NEGATIVE),
        children = listOf(
            Bar(SPEED_COLOR) { gameSession.movementManager.intention },
            Bar(STAMINA_COLOR) { gameSession.movementManager.stamina },
        ),
        pivot = Pivot.BOTTOM_LEFT,
        onRender = {
            it.visible = !renderer.hudHidden && renderer.isFirstPerson && !gameSession.mainPlayer.isSpectating
        }
    )
}