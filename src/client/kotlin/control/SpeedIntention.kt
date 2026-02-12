package org.lain.engine.client.control

import org.lain.engine.client.GameSession
import org.lain.engine.player.MovementStatus
import org.lain.engine.util.require

class MovementManager(
    private val gameSession: GameSession
) {
    var locked = true
    var intention: Float = 0.5f
        private set
    var stamina = 0f

    fun roll(delta: Float) {
        if (locked) return
        val value = delta / 18f
        intention = (intention + value).coerceIn(0.0f, 1f)

        gameSession.mainPlayer.require<MovementStatus>().intention = intention
        gameSession.handler.onSpeedIntentionUpdate(intention)
    }
}