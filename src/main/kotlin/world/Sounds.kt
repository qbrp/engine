package org.lain.engine.world

import org.lain.engine.item.*
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.flush
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
        val pitch: Float = 1f
    ) : WorldSoundPlayRequest()
}

fun processWorldSounds(
    handler: ServerHandler,
    soundEventStorage: SoundEventStorage,
    defaultItemSounds: Map<String, SoundEventId>,
    world: World
) {
    world.events.sounds.flush { request ->
        val play = when(request) {
            is WorldSoundPlayRequest.Positioned -> SoundPlay(
                soundEventStorage.getOrSingleSound(request.eventId),
                request.pos,
                request.category,
                request.volume,
                request.pitch
            )
            is WorldSoundPlayRequest.Item -> SoundPlay(
                soundEventStorage.getOrSingleSound(
                    request.item.sound?.get(request.key) ?: defaultItemSounds[request.key] ?: SoundEventId.MISSING,
                ),
                request.item.pos,
                request.category,
                request.volume,
                request.pitch
            )
            is WorldSoundPlayRequest.Simple -> request.play
        }

        val distance = play.volume * play.sound.sources.maxOf { it.distance }
        val receivers = world.players.filter { player -> player.pos.squaredDistanceTo(play.pos) <= distance * distance }
        handler.onSoundEvent(play, receivers)
    }
}

fun World.emitPlaySoundEvent(request: WorldSoundPlayRequest) {
    events.sounds.add(request)
}

fun World.emitPlaySoundEvent(play: SoundPlay) = emitPlaySoundEvent(WorldSoundPlayRequest.Simple(play))

fun World.emitPlaySoundEvent(
    event: SoundEventId,
    pos: Vec3,
    category: EngineSoundCategory,
    volume: Float = 1f,
    pitch: Float = 1f
) = events.sounds.add(
    WorldSoundPlayRequest.Positioned(
        event,
        pos,
        category,
        volume,
        pitch
    )
)