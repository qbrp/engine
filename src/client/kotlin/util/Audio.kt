package org.lain.engine.client.util

import org.lain.cyberia.ecs.iterate
import org.lain.engine.script.NamespacedStorageAccess
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
    fun playSound(event: SoundEvent, parameters: SoundEmissionParameters)
    fun containsAudioSource(slot: String): Boolean
    fun addAudioSource(audioSource: AudioSource, slot: String)
    fun stopAudioSource(audioSource: AudioSource)
    fun invalidateCache()
}

fun World.processWorldSounds(
    storage: NamespacedStorageAccess,
    audioManager: EngineAudioManager
) {
    iterate<SoundEmission>() { _, emission ->
        val event = storage.sounds[emission.sound] ?: return@iterate
        audioManager.playSound(event, emission.parameters)
    }
}