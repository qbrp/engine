package org.lain.engine.client.render.ui

import org.lain.engine.client.GameSession
import net.minecraft.client.gui.GuiGraphics
import org.lain.engine.player.isSpectating
import org.lain.engine.util.Color
import org.lain.engine.util.SPEED_COLOR
import org.lain.engine.util.STAMINA_COLOR
import org.lain.engine.util.math.lerp
import kotlin.math.abs
import kotlin.math.pow

data class MovementStatusRenderState(
    var speedWidth: Float = 0f,
    var staminaWidth: Float = 0f,
    var opacity: Float = 0f,
    var unchangedTicks: Float = MOVEMENT_STATUS_HIDE_DELAY_TICKS,
    var lastIntention: Float? = null,
    var lastStamina: Float? = null,
)

private const val BAR_WIDTH = 64f
private const val BAR_HEIGHT = 2f
private const val BAR_GAP = 1f
private const val BAR_X = 2f
private const val BAR_BOTTOM_PADDING = 3f
private const val MOVEMENT_STATUS_HIDE_DELAY_TICKS = 30f
private const val MOVEMENT_STATUS_CHANGE_EPSILON = 0.0005f

fun renderMovementStatus(
    context: GuiGraphics,
    renderState: MovementStatusRenderState,
    gameSession: GameSession,
    dt: Float
) {
    val renderer = gameSession.renderer
    val shouldRenderHud = !renderer.hudHidden && renderer.isFirstPerson && !gameSession.mainPlayer.isSpectating && !renderer.chatOpen

    val intention = gameSession.movementManager.intention.coerceIn(0f, 1f)
    val stamina = gameSession.movementManager.stamina.coerceIn(0f, 1f)
    val changed = renderState.lastIntention?.let { abs(it - intention) > MOVEMENT_STATUS_CHANGE_EPSILON } ?: true ||
        renderState.lastStamina?.let { abs(it - stamina) > MOVEMENT_STATUS_CHANGE_EPSILON } ?: true

    if (changed) {
        renderState.unchangedTicks = 0f
        renderState.lastIntention = intention
        renderState.lastStamina = stamina
    } else {
        renderState.unchangedTicks += dt
    }

    val targetOpacity = if (shouldRenderHud && renderState.unchangedTicks <= MOVEMENT_STATUS_HIDE_DELAY_TICKS) 1f else 0f
    renderState.opacity = lerp(renderState.opacity, targetOpacity, 1f - 0.7f.pow(dt))
    if (renderState.opacity <= 0.01f) return

    renderState.speedWidth = lerp(renderState.speedWidth, BAR_WIDTH * intention, 0.5f)
    renderState.staminaWidth = lerp(renderState.staminaWidth, BAR_WIDTH * stamina, 0.5f)

    val bottom = context.guiHeight() - BAR_BOTTOM_PADDING
    renderBar(
        context,
        BAR_X,
        bottom - BAR_HEIGHT * 2f - BAR_GAP,
        renderState.speedWidth,
        SPEED_COLOR,
        renderState.opacity
    )
    renderBar(
        context,
        BAR_X,
        bottom - BAR_HEIGHT,
        renderState.staminaWidth,
        STAMINA_COLOR,
        renderState.opacity
    )
}

private fun renderBar(
    context: GuiGraphics,
    x: Float,
    y: Float,
    width: Float,
    color: Color,
    opacity: Float
) {
    context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, Color.BLACK.withOpacity(opacity).integer)
    context.fill(x, y, x + width.coerceIn(0f, BAR_WIDTH), y + BAR_HEIGHT, color.withOpacity(opacity).integer)
}

private fun Color.withOpacity(opacity: Float): Color {
    return withAlpha((alpha * opacity).toInt().coerceIn(0, 255))
}
