package org.lain.engine.client.chat

import org.lain.engine.client.GameSession

data class PlayerVolume(
    var value: Float,
    var max: Float,
    var base: Float
)

class PlayerVocalRegulator(
    val volume: PlayerVolume,
    private val gameSession: GameSession
) {
    val current
        get() = volume.value

    fun reset() {
        set(volume.base)
    }

    fun increase(value: Float) {
        set(volume.value + value)
    }

    fun decrease(value: Float) {
        set(volume.value - value)
    }

    fun set(value: Float) {
        val old = volume.value
        if (old == value) return
        val value = volume.updateVolume(value)
        gameSession.chatEventBus.onMessageVolumeUpdate(old, value)
        gameSession.handler.onVolumeUpdate(value)
    }
}

fun PlayerVolume.updateVolume(vol: Float): Float {
    value = vol.coerceIn(0f, max)
    return value
}