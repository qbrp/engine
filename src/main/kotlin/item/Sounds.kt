package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.Pos
import org.lain.engine.util.Vec3
import org.lain.engine.util.get
import org.lain.engine.util.require
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.WorldSoundPlayRequest
import org.lain.engine.world.WorldSoundsComponent
import org.lain.engine.world.emitPlaySoundEvent
import org.lain.engine.world.pos
import org.lain.engine.world.world

data class ItemSounds(val sounds: Map<String, SoundEventId>) : Component

@Serializable
data class SoundEvent(val id: SoundEventId, val sources: List<SoundSource>)

@Serializable
data class SoundPlay(
    val sound: SoundEvent,
    val pos: Vec3,
    val category: EngineSoundCategory,
    val volume: Float = 1f,
    val pitch: Float = 1f,
)

enum class EngineSoundCategory {
    MASTER, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE
}

@JvmInline
@Serializable
value class SoundEventId(val value: String) {
    override fun toString(): String = value.toString()
}

@Serializable
data class SoundSource(
    val id: SoundId,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val weight: Int = 1,
    val distance: Int = 16
)

@JvmInline
@Serializable
value class SoundId(val value: String) {
    override fun toString(): String = value.toString()
}

typealias SoundEventStorage = NamespacedStorage<SoundEventId, SoundEvent>

fun SoundEventStorage.getOrSingleSound(id: SoundEventId) = this.entries[id] ?: SoundEvent(
    id,
    listOf(
        SoundSource(
            SoundId(id.value)
        )
    )
)

fun EngineItem.emitPlaySoundEvent(
    key: String,
    category: EngineSoundCategory,
    pos: Vec3 = this.pos,
    volume: Float = 1f,
    pitch: Float = 1f,
    itemSounds: ItemSounds? = this.get(),
    default: () -> SoundEventId = { SoundEventId("missing") }
) {
    this.world.emitPlaySoundEvent(itemSounds?.sounds[key] ?: default(), pos, category, volume, pitch)
}