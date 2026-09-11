package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptValue

@Serializable
data class CustomPlayerAttributes(
    var speed: Float? = null,
    var jumpStrength: Float? = null,
    var gravity: Float? = null,
    val script: MutableMap<String, ScriptValue> = mutableMapOf()
) : Component {
    fun copy() = copy(script = script.toMutableMap())
}

@Serializable
data class PlayerAttributes(
    var speed: Float = 0.055f,
    var jumpStrength: Float = 0.37f,
    var gravity: Float = 0.98f,
    var flySpeed: Float = 1f,
) : Component

enum class PlayerStatus {
    DEFAULT, GM, SPECTATING;

    companion object {
        fun of(gameMaster: Boolean, spectating: Boolean) = when {
            gameMaster -> PlayerStatus.GM
            spectating -> PlayerStatus.SPECTATING
            else -> PlayerStatus.DEFAULT
        }
    }
}

val EnginePlayer.attributes
    get() = this.require<PlayerAttributes>()

fun EnginePlayer.setCustomSpeed(speed: Float) {
    require<CustomPlayerAttributes>().speed = speed
    markUpdated<CustomPlayerAttributes>()
}

fun EnginePlayer.resetCustomSpeed() {
    require<CustomPlayerAttributes>().speed = null
    markUpdated<CustomPlayerAttributes>()
}

fun EnginePlayer.setCustomJumpStrength(value: Float) {
    require<CustomPlayerAttributes>().jumpStrength = value
    markUpdated<CustomPlayerAttributes>()
}

fun EnginePlayer.resetCustomJumpStrength() {
    require<CustomPlayerAttributes>().jumpStrength = null
    markUpdated<CustomPlayerAttributes>()
}