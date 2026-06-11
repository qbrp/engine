package org.lain.engine.client.chat

import org.lain.engine.transport.packet.ClientChatSettings

interface ChatEventBus {
    fun addToGui(message: AcceptedMessage, visible: Boolean)
    fun onMessageDelete(message: AcceptedMessage)
    fun onSettingsUpdate(settings: ClientChatSettings, chatBar: ChatBar)
    fun invalidateChatEntries()
    fun onHideChatBarSection(section: ChatBarSection, state: ChatBarSectionState)
    fun onMessageVolumeUpdate(old: Float, new: Float)
}