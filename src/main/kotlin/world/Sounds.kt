package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.item.EngineItem
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.EngineId
import org.lain.engine.script.Identifiable
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.util.math.EVec3
import org.lain.engine.util.math.ImmutableEVec3

@Serializable
data class SoundEvent(val id: SoundEventId, val sources: List<ESoundSource>)

@Serializable
data class SoundPlay(
    val sound: SoundEvent,
    val pos: ImmutableEVec3,
    val category: EngineSoundCategory,
    val volume: Float = 1f,
    val pitch: Float = 1f,
)

fun SoundPlay(sound: SoundEvent, pos: EVec3, category: EngineSoundCategory = EngineSoundCategory.AMBIENT, volume: Float = 1f, pitch: Float = 1f) =
    SoundPlay(sound, ImmutableEVec3(pos), category, volume, pitch)

enum class EngineSoundCategory {
    MASTER, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE;
}

@JvmInline
@Serializable
value class SoundEventId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString(): String = value.toString()

    companion object {
        val MISSING = SoundEventId(EngineId("missing"))
    }
}

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

fun NamespacedStorageAccess.getOrSingleSound(id: SoundEventId) = this.sounds[id] ?: SoundEvent(
    id,
    listOf(
        ESoundSource(
            SoundId(id.value.namespace)
        )
    )
)

sealed class WorldSoundPlayRequest : Component {
    data class Simple(val play: SoundPlay) : WorldSoundPlayRequest()
    data class Positioned(
        val eventId: SoundEventId,
        val pos: EVec3,
        val category: EngineSoundCategory,
        val volume: Float = 1f,
        val pitch: Float = 1f
    ) : WorldSoundPlayRequest()
    data class Item(
        val item: EngineItem,
        val key: String,
        val category: EngineSoundCategory,
        val volume: Float = 1f,
        val pitch: Float = 1f,
        val player: EnginePlayer? = null,
    ) : WorldSoundPlayRequest()
}

fun World.emitPlaySoundEvent(sound: WorldSoundPlayRequest) = emitEvent<WorldSoundPlayRequest>(sound)

fun World.emitPlaySoundEvent(sound: SoundPlay) = emitEvent<WorldSoundPlayRequest>(WorldSoundPlayRequest.Simple(sound))

fun World.emitPlaySoundEvent(
    event: SoundEventId,
    pos: EVec3,
    category: EngineSoundCategory,
    volume: Float = 1f,
    pitch: Float = 1f
) = emitEvent<WorldSoundPlayRequest>(
    WorldSoundPlayRequest.Positioned(
        event,
        pos,
        category,
        volume,
        pitch
    )
)