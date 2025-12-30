package org.lain.engine.client.control

import org.lain.engine.client.handler.ClientHandler

class MovementManager(
    private val handler: ClientHandler
) {
    var locked = true
    var intention: Float = 0.5f
        private set
    var stamina = 0f

    fun roll(delta: Float) {
        if (locked) return
        val value = delta / 18f
        intention = (intention + value).coerceIn(0.0f, 1f)
        handler.onSpeedIntentionUpdate(intention)
    }
}