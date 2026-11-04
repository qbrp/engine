package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptValue

val BUILTIN_VOICE_PRESETS = listOf(
    "aa_blip",
    "aa_blip_typewriter",
    "aa_dd_blip",
    "aa_tgaa_blip",
    "ber",
    "bullet",
    "c2",
    "c3",
    "c4",
    "cn3",
    "cn4",
    "impact",
    "noe",
    "note",
    "q",
    "rud",
    "voice_susie",
    "voice_toriel",
    "voice_undyne",
    "wilson_bark",
    "woodie_bark",
    "wurt_bark",
    "wx78_bark",
)
const val DEFAULT_VOICE_PRESET = "c3"

@Serializable
data class CustomPlayerAttributes(
    var speed: Float? = null,
    var jumpStrength: Float? = null,
    var gravity: Float? = null,
    val script: MutableMap<String, ScriptValue> = mutableMapOf(),
    var voiceSet: String? = null,
    var voiceRandomization: Float? = null,
    var voicePitch: Float? = null,
    var voiceSpeed: Float? = null,
    var voiceSoundFrequency: Float? = null,
) : Component {
    fun copy() = copy(script = script.toMutableMap())
}

@Serializable
data class PlayerAttributes(
    var speed: Float = 0.055f,
    var jumpStrength: Float = 0.37f,
    var gravity: Float = 0.98f,
    var flySpeed: Float = 1f,
    var voiceSet: String = DEFAULT_VOICE_PRESET,
    var voiceRandomization: Float = 0.1f,
    var voicePitch: Float = 1f,
    var voiceSpeed: Float = 0.6f,
    var voiceSoundFrequency: Float = 0.9f,
) : Component

fun PlayerAttributes.withCustom(custom: CustomPlayerAttributes?): PlayerAttributes = copy(
    speed = custom?.speed ?: speed,
    jumpStrength = custom?.jumpStrength ?: jumpStrength,
    gravity = custom?.gravity ?: gravity,
    voiceSet = custom?.voiceSet ?: voiceSet,
    voiceRandomization = custom?.voiceRandomization ?: voiceRandomization,
    voicePitch = custom?.voicePitch ?: voicePitch,
    voiceSpeed = custom?.voiceSpeed ?: voiceSpeed,
    voiceSoundFrequency = custom?.voiceSoundFrequency ?: voiceSoundFrequency,
)

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

val EnginePlayer.effectiveAttributes
    get() = attributes.withCustom(get<CustomPlayerAttributes>())

fun EnginePlayer.editCustomAttributes(edit: CustomPlayerAttributes.() -> Unit) {
    require<CustomPlayerAttributes>().edit()
    markUpdated<CustomPlayerAttributes>()
}

fun EnginePlayer.setCustomSpeed(speed: Float) {
    editCustomAttributes { this.speed = speed }
}

fun EnginePlayer.resetCustomSpeed() {
    editCustomAttributes { speed = null }
}

fun EnginePlayer.setCustomJumpStrength(value: Float) {
    editCustomAttributes { jumpStrength = value }
}

fun EnginePlayer.resetCustomJumpStrength() {
    editCustomAttributes { jumpStrength = null }
}
