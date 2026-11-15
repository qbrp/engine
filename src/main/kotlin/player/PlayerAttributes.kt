package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptValue

@Serializable
data class CustomPlayerAttributes(
    var speed: Float? = null,
    var jumpStrength: Float? = null,
    var gravity: Float? = null,
    val script: MutableMap<String, ScriptValue> = mutableMapOf(),
) : Component {
    fun copy() = copy(script = script.mapValuesTo(mutableMapOf()) { (_, value) -> value.copy() })
}

@Serializable
data class PlayerAttributes(
    var speed: Float = 0.055f,
    var jumpStrength: Float = 0.37f,
    var gravity: Float = 0.98f,
    var flySpeed: Float = 1f
) : Component

fun PlayerAttributes.withCustom(custom: CustomPlayerAttributes?): PlayerAttributes =
    copy(
        speed = custom?.speed ?: speed,
        jumpStrength = custom?.jumpStrength ?: jumpStrength,
        gravity = custom?.gravity ?: gravity
    )

val EnginePlayer.attributes
    get() = this.require<PlayerAttributes>()

val EnginePlayer.effectiveAttributes
    get() = attributes.withCustom(get<CustomPlayerAttributes>())

fun EnginePlayer.editCustomAttributes(edit: CustomPlayerAttributes.() -> Unit) {
    require<CustomPlayerAttributes>().edit()
    markUpdated<CustomPlayerAttributes>()
}
