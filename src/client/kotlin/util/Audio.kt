package org.lain.engine.client.util

import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.util.flush
import org.lain.engine.world.EngineSoundCategory
import org.lain.engine.world.SoundBroadcast
import org.lain.engine.world.SoundId
import org.lain.engine.world.SoundPlay
import java.util.*

data class AudioSource(
    val sound: SoundId,
    val category: EngineSoundCategory,
    var x: Float,
    var y: Float,
    var z: Float,
    var isRelative: Boolean,
    var volume: Float,
    var pitch: Float,
    var attenuate: Boolean,
    var slot: String? = null,
    var isEnded: Boolean = false
)

interface EngineAudioManager {
    fun playUiNotificationSound()
    fun playPigScreamSound()
    fun playKickSound()
    fun playSound(player: SoundPlay)
    fun containsAudioSource(slot: String): Boolean
    fun addAudioSource(audioSource: AudioSource, slot: String)
    fun stopAudioSource(audioSource: AudioSource)
    fun invalidateCache()
}

fun processSoundPlayKeys(queue: Queue<SoundBroadcast>, handler: ClientHandler, audioManager: EngineAudioManager) = queue.flush {
    // TODO: Сделать более глубокую проверку, не только по идентификатору
    if (!handler.processedSounds.any { sound -> sound.context == it.context && sound.play.sound.id == it.play.sound.id } || it.context == null) {
        audioManager.playSound(it.play)
        handler.processedSounds.add(it)
    }
}