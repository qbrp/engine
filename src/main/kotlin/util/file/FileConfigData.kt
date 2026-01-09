package org.lain.engine.util.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.server.ServerId

@Serializable
data class ServerConfig(
    val server: ServerId,
    val chat: ChatConfig,
    val player: PlayerConfig,
    val vocal: VoiceApparatusConfig,
    val movement: MovementConfig
)


//// CHAT CONFIG


@Serializable
data class ChatConfig(
    val channels: Map<String, ChannelConfig>,
    @SerialName("default-channel") val defaultChannel: String? = null,
    val placeholders: Map<String, String> = mapOf(),
    val commands: Map<String, ChannelConfig> = mapOf(),
    val pm: String,
    val join: ExclamationMessageConfig,
    val leave: ExclamationMessageConfig,
    val acoustic: RealisticAcousticConfig
)

@Serializable
data class ExclamationMessageConfig(
    val enabled: Boolean,
    val message: String,
)

// Channels

@Serializable
data class ChannelConfig(
    val format: String,
    val name: String? = null,
    val prefix: String? = null,
    val regex: RegexConfig? = null,
    val global: Boolean = false,
    val distance: Int? = null,
    val acoustic: AcousticConfig? = null,
    val spectator: Boolean = false,
    val speech: Boolean = false,
    val notify: Boolean = false,
    val permission: Boolean = false,
    val heads: Boolean = false
)

@Serializable
data class RegexConfig(
    val exp: String,
    val remove: String? = null
)

@Serializable
data class AcousticConfig(val distort: Boolean = false)

// Acoustic

@Serializable
data class RealisticAcousticConfig(
    val formatting: List<AcousticLevelConfig>,
    val distortion: DistortionConfig,
    val passability: AcousticBlockConfig,
    val simulation: AcousticSimulationConfig,
    val volume: AcousticVolumeConfig
)

@Serializable
data class AcousticLevelConfig(
    val volume: Float,
    val multiplier: Float = 1f,
    val placeholders: Map<String, String> = mapOf()
)

@Serializable
data class AcousticVolumeConfig(
    @SerialName("hearing-threshold") val hearingThreshold: Float,
    val max: Float
)

@Serializable
data class AcousticBlockConfig(
    val solid: Float,
    val air: Float,
    val partial: Float,
    val blocks: Map<String, Float> = mapOf(),
    val tags: Map<String, Float> = mapOf(),
)

@Serializable
data class AcousticSimulationConfig(
    val steps: Int,
    @SerialName("chunk-size") val chunkSize: Int,
    val range: Int,
    @SerialName("performance-debug") val performanceDebug: Boolean
)

@Serializable
data class DistortionConfig(
    val threshold: Float,
    val artifacts: List<Char>
)

//// GAME MECHANICS

@Serializable
data class VoiceApparatusConfig(
    @SerialName("break_threshold") val breakThreshold: Float,
    @SerialName("break_chance") val breakChance: Float,
    @SerialName("regeneration_time") val regenerationTimeSeconds: Int,
    @SerialName("regeneration_random") val regenerationTimeRandom: Float,
    @SerialName("tiredness_threshold") val tirednessThreshold: Float,
    @SerialName("tiredness_gain") val tirednessGain: Float,
    @SerialName("tiredness_decrease_rate") val tirednessDecreaseRateSeconds: Float
)

@Serializable
data class MovementConfig(
    @SerialName("sprint_multiplier") val sprintMultiplier: Float,
    @SerialName("min_speed_factor") val minSpeedFactor: Float,
    @SerialName("min_speed_multiplier_sprint_factor") val minSpeedSprintFactor: Float,
    @SerialName("slowdown_stamina_threshold") val slowdownStaminaThreshold: Float,
    @SerialName("stamina_consumption") val staminaConsumeMinutes: Float,
    @SerialName("stamina_regen") val staminaRegenMinutes: Float,
    @SerialName("intention_effect") val intentionEffect: Float
)

//// PLAYER CONFIG


@Serializable
data class PlayerConfig(
    val attributes: Map<String, Map<String, Float>>,
    val volume: VocalVolumeConfig,
    val damage: Boolean
)

@Serializable
data class VocalVolumeConfig(
    val base: Float,
    val min: Float,
    val max: Float
)
