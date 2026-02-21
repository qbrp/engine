package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.engine.item.*
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.InteractionId
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.flushMap
import org.lain.engine.util.math.Vec3

sealed class WorldSoundPlayRequest {
    data class Simple(val play: SoundPlay) : WorldSoundPlayRequest()
    data class Positioned(
        val eventId: SoundEventId,
        val pos: Vec3,
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
    storage: NamespacedStorage,
    world: World
): List<SoundBroadcast> {
    return world.events<WorldSoundPlayRequest>().flushMap { request ->
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
                        request.item.sound?.get(request.key) ?: SoundEventId.MISSING,
                    ),
                    request.item.pos,
                    request.category,
                    request.volume,
                    request.pitch
                )
            }
            is WorldSoundPlayRequest.Simple -> request.play
        }
        val distance = play.volume * play.sound.sources.maxOf { it.distance }
        val receivers = players.filter { player -> player.pos.squaredDistanceTo(play.pos) <= distance * distance }
        SoundBroadcast(play, receivers, context)
    }
}

fun broadcastWorldSounds(sounds: List<SoundBroadcast>, handler: ServerHandler) = sounds.forEach { (play, listeners, context) ->
    handler.onSoundEvent(play, context, listeners)
}

fun World.emitPlaySoundEvent(request: WorldSoundPlayRequest) {
    events<WorldSoundPlayRequest>().add(request)
}

fun World.emitPlaySoundEvent(play: SoundPlay) = emitPlaySoundEvent(WorldSoundPlayRequest.Simple(play))

fun World.emitPlaySoundEvent(
    event: SoundEventId,
    pos: Vec3,
    category: EngineSoundCategory,
    volume: Float = 1f,
    pitch: Float = 1f
) = events<WorldSoundPlayRequest>().add(
    WorldSoundPlayRequest.Positioned(
        event,
        pos,
        category,
        volume,
        pitch
    )
)