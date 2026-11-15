package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component

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
    "susie",
    "toriel",
    "undyne",
    "temmie",
    "sans",
    "ralsei",
    "papyrus",
    "wilson",
    "woodie",
    "wurt",
    "wx78",
)
const val DEFAULT_VOICE_PRESET = "c3"

@Serializable
data class VoiceImage(
    var preset: String = DEFAULT_VOICE_PRESET,
    var randomization: Float = 0.1f,
    var pitch: Float = 1f,
    var speed: Float = 0.6f,
    var frequency: Float = 0.9f,
) : Component
