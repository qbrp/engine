package org.lain.engine.client.util

import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.item.SoundPlay
import org.lain.engine.util.flush
import org.lain.engine.world.SoundBroadcast
import java.util.*

interface EngineAudioManager {
    fun playUiNotificationSound()
    fun playPigScreamSound()
    fun playSound(player: SoundPlay)
    fun invalidateCache()
}

fun processSoundPlayKeys(queue: Queue<SoundBroadcast>, handler: ClientHandler, audioManager: EngineAudioManager) = queue.flush {
    // TODO: Сделать более глубокую проверку, не только по идентификатору
    if (!handler.processedSounds.any { sound -> sound.context == it.context && sound.play.sound.id == it.play.sound.id } || it.context == null) {
        audioManager.playSound(it.play)
        handler.processedSounds.add(it)
    }
}