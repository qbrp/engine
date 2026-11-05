package org.lain.engine.client.chat

interface ChatEventBus {
    fun onMessageVolumeUpdate(old: Float, new: Float)
}