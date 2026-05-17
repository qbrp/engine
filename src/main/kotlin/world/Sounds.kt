package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemSounds
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.InteractionId
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.server.ServerHandler
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
value class SoundEventId(val value: String) {
    override fun toString(): String = value

    companion object {
        val MISSING = SoundEventId("missing")
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
            SoundId(id.value)
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
        val context: SoundContext? = null,
    ) : WorldSoundPlayRequest()
}

@Serializable
data class SoundContext(
    val index: Int,
    val interaction: InteractionId?
)

data class SoundBroadcast(val play: SoundPlay, val listeners: List<EnginePlayer>, val context: SoundContext?)

fun processWorldSounds(
    storage: NamespacedStorageAccess,
    world: World
): List<SoundBroadcast> {
    val broadcasts = mutableListOf<SoundBroadcast>()
    world.iterate<WorldSoundPlayRequest> { _, request ->
        var players = world.players.toList()
        var context: SoundContext? = null
        val play = when(request) {
            is WorldSoundPlayRequest.Positioned -> SoundPlay(
                storage.getOrSingleSound(request.eventId),
                request.pos,
                request.category,
                request.volume,
                request.pitch
            )
            is WorldSoundPlayRequest.Item -> {
                if (request.player != null) {
                    players = listOf(request.player)
                }
                context = request.context
                SoundPlay(
                    storage.getOrSingleSound(
                        request.item.getComponent<ItemSounds>()?.sounds?.get(request.key) ?: SoundEventId.MISSING,
                    ),
                    request.item.getComponent<Location>()?.position ?: ImmutableEVec3(),
                    request.category,
                    request.volume,
                    request.pitch
                )
            }
            is WorldSoundPlayRequest.Simple -> request.play
        }
        val distance = play.volume * play.sound.sources.maxOf { it.distance }
        val receivers = players.filter { player -> player.pos.squaredDistanceTo(play.pos) <= distance * distance }
        broadcasts += SoundBroadcast(play, receivers, context)
    }
    return broadcasts
}

fun broadcastWorldSounds(sounds: List<SoundBroadcast>, handler: ServerHandler) = sounds.forEach { (play, listeners, context) ->
    handler.onSoundEvent(play, context, listeners)
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