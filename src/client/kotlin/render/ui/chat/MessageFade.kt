package org.lain.engine.client.render.ui.chat

class MessageFade(
    private var startTick: Long,
    private var blockedAtTick: Long? = null,
) {
    fun update(canFade: Boolean, tick: Long) {
        val blockedAt = blockedAtTick
        if (!canFade && blockedAt == null) {
            blockedAtTick = tick
        } else if (canFade && blockedAt != null) {
            startTick += (tick - blockedAt).coerceAtLeast(0L)
            blockedAtTick = null
        }
    }

    fun alpha(tick: Long, lifetime: Float, enabled: Boolean): Float {
        if (!enabled || blockedAtTick != null) return 1f

        val age = (tick - startTick).coerceAtLeast(0L)
        val opacity = ((1.0 - age / lifetime.toDouble()) * 10.0).coerceIn(0.0, 1.0)
        return (opacity * opacity).toFloat()
    }

    fun copy() = MessageFade(startTick, blockedAtTick)
}
