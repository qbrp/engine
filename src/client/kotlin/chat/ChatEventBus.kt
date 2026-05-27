package org.lain.engine.client.chat

import org.lain.engine.transport.packet.ClientChatSettings

interface ChatEventBus {
    fun addToGui(message: AcceptedMessage)
    fun onMessageDelete(message: AcceptedMessage)
    fun onSettingsUpdate(settings: ClientChatSettings, chatBar: ChatBar)
    fun invalidateChatEntries()
    fun onMessageVolumeUpdate(old: Float, new: Float)
}