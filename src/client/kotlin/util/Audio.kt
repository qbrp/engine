package org.lain.engine.client.util

import org.lain.engine.item.SoundPlay

interface EngineAudioManager {
    fun playUiNotificationSound()
    fun playPigScreamSound()
    fun playSound(player: SoundPlay)
    fun invalidateCache()
}