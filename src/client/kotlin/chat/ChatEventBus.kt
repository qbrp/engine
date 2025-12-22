package org.lain.engine.client.chat

import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings

interface ChatEventBus {
    fun onMessageAdd(message: EngineChatMessage)
    fun onChannelEnable(channel: ClientChatChannel)
    fun onChannelDisable(channel: ClientChatChannel)
    fun onSettingsUpdate(settings: ClientChatSettings, chatBar: ChatBar)
    fun onSpyEnable()
    fun onSpyDisable()
    fun onMessageVolumeUpdate(old: Float, new: Float)
}