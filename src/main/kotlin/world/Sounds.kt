package org.lain.engine.world

import org.lain.engine.item.EngineSoundCategory
import org.lain.engine.item.SoundEventId
import org.lain.engine.item.SoundEventStorage
import org.lain.engine.item.SoundPlay
import org.lain.engine.item.getOrSingleSound
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.Component
import org.lain.engine.util.Vec3
import org.lain.engine.util.flush
import org.lain.engine.util.require
import java.util.LinkedList
import java.util.Queue

sealed class WorldSoundPlayRequest {
    data class Simple(val play: SoundPlay) : WorldSoundPlayRequest()
    data class Positioned(
        val eventId: SoundEventId,
        val pos: Vec3,
        val category: EngineSoundCategory,
        val volume: Float = 1f,
        val pitch: Float = 1f
    ) : WorldSoundPlayRequest()
}

data class WorldSoundsComponent(val events: Queue<WorldSoundPlayRequest> = LinkedList()) : Component

fun processWorldSounds(handler: ServerHandler, soundEventStorage: SoundEventStorage, world: World) {
    world.require<WorldSoundsComponent>().events.flush { request ->
        val play = when(request) {
            is WorldSoundPlayRequest.Positioned -> SoundPlay(
                soundEventStorage.getOrSingleSound(request.eventId),
                request.pos,
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

fun World.emitPlaySoundEvent(play: SoundPlay) = this.require<WorldSoundsComponent>().events.add(WorldSoundPlayRequest.Simple(play))

fun World.emitPlaySoundEvent(
    event: SoundEventId,
    pos: Vec3,
    category: EngineSoundCategory,
    volume: Float = 1f,
    pitch: Float = 1f
) = this.require<WorldSoundsComponent>().events.add(
    WorldSoundPlayRequest.Positioned(
        event,
        pos,
        category,
        volume,
        pitch
    )
)