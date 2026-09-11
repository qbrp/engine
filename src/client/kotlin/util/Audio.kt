package org.lain.engine.client.util

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.ItemSounds
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.world.*

data class SoundParameters(val id: SoundId, val stream: Boolean)

class AudioSource(
    val sound: SoundParameters,
    val category: EngineSoundCategory,
    var x: Float,
    var y: Float,
    var z: Float,
    var volume: Float,
    var pitch: Float,
    var spatial: Boolean,
    var radius: Int,
    var looping: Boolean = false,
    var slot: String? = null,
    var isEnded: Boolean = false,
)

interface EngineAudioManager {
    fun playUiNotificationSound()
    fun playPigScreamSound()
    fun playKickSound()
    fun playSound(player: SoundPlay, ignorePhysics: Boolean = false)
    fun containsAudioSource(slot: String): Boolean
    fun addAudioSource(audioSource: AudioSource, slot: String)
    fun stopAudioSource(audioSource: AudioSource)
    fun invalidateCache()
}

fun World.processWorldSounds(
    storage: NamespacedStorageAccess,
    audioManager: EngineAudioManager
) {
    iterate<WorldSoundPlayRequest> { _, request ->
        val play = when(request) {
            is WorldSoundPlayRequest.Positioned -> SoundPlay(
                storage.getOrSingleSound(request.eventId),
                request.pos,
                request.category,
                request.volume,
                request.pitch
            )
            is WorldSoundPlayRequest.Item -> {
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
        audioManager.playSound(play)
    }
}