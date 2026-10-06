package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.script.EngineId
import org.lain.engine.script.Identifiable
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.util.math.ImmutableEVec3

enum class EngineSoundCategory {
    MASTER, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE;
}

@Serializable
data class SoundEvent(val id: SoundEventId, val sources: List<ESoundSource>)

@Serializable
data class SoundEmission(
    val sound: SoundEventId,
    val parameters: SoundEmissionParameters
) : Component

@Serializable
data class SoundEmissionParameters(
    val pos: ImmutableEVec3,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val category: EngineSoundCategory = EngineSoundCategory.AMBIENT,
    val ignorePhysics: Boolean = false
)

@JvmInline
@Serializable
value class SoundEventId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString(): String = value.toString()
}

fun EngineId.toSoundEventId() = SoundEventId(this)

fun String.toSoundId() = SoundId(this)

@Serializable
data class ESoundSource(
    val id: SoundId,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val weight: Int = 1,
    val distance: Int = 16,
    val pitchRandom: Float = 0f
)

@JvmInline
@Serializable
value class SoundId(val value: String) {
    override fun toString(): String = value
}

fun NamespacedStorageAccess.getEventOrSound(id: SoundEventId) = this.sounds[id] ?: SoundEvent(
    id,
    listOf(
        ESoundSource(
            SoundId(id.value.namespace)
        )
    )
)