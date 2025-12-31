package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.MessageSource
import org.lain.engine.transport.packet.ClientChatChannel

data class EngineChatMessage(
    val text: String,
    val display: String,
    val channel: ClientChatChannel,
    val source: MessageSource,
    val isMentioned: Boolean = false,
    val isSpeech: Boolean = false,
    val volume: Float? = null,
    val isSpy: Boolean = false,
    val showHead: Boolean = false,
    val id: MessageId
)

val SYSTEM_CHANNEL = ClientChatChannel(
    ChannelId("system"),
    "Система",
    format = "{text}",
    spy = false
)