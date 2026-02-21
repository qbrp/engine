package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.get
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.SoundContext
import org.lain.engine.world.WorldSoundPlayRequest
import org.lain.engine.world.emitPlaySoundEvent
import org.lain.engine.world.world

@Serializable
data class ItemSounds(val sounds: Map<String, SoundEventId>) : ItemComponent

val EngineItem.sound
    get() = this.get<ItemSounds>()?.sounds

@Serializable
data class SoundEvent(val id: SoundEventId, val sources: List<SoundSource>)

@Serializable
data class SoundPlay(
    val sound: SoundEvent,
    val pos: ImmutableVec3,
    val category: EngineSoundCategory,
    val volume: Float = 1f,
    val pitch: Float = 1f,
)

fun SoundPlay(sound: SoundEvent, pos: Vec3, category: EngineSoundCategory = EngineSoundCategory.AMBIENT, volume: Float = 1f, pitch: Float = 1f) =
    SoundPlay(sound, ImmutableVec3(pos), category, volume, pitch)

enum class EngineSoundCategory {
    MASTER, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE
}

@JvmInline
@Serializable
value class SoundEventId(val value: String) {
    override fun toString(): String = value

    companion object {
        val MISSING = SoundEventId("missing")
    }
}

@Serializable
data class SoundSource(
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

fun NamespacedStorage.getOrSingleSound(id: SoundEventId) = this.sounds[id] ?: SoundEvent(
    id,
    listOf(
        SoundSource(
            SoundId(id.value)
        )
    )
)

fun EngineItem.emitPlaySoundEvent(
    key: String,
    category: EngineSoundCategory = EngineSoundCategory.AMBIENT,
    volume: Float = 1f,
    pitch: Float = 1f,
    player: EnginePlayer? = null,
    context: SoundContext? = null
) {
    this.world.emitPlaySoundEvent(
        WorldSoundPlayRequest.Item(this, key, category, volume, pitch, player, context)
    )
}