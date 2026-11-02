package org.lain.engine.client.chat

import org.lain.engine.transport.packet.ClientChatSettings

interface ChatEventBus {
    fun onMessageVolumeUpdate(old: Float, new: Float)
}