package org.lain.engine.client.chat

import org.lain.engine.chat.MessageSource
import org.lain.engine.transport.packet.ClientChatChannel

data class EngineChatMessage(
    val text: String,
    val display: String,
    val channel: ClientChatChannel,
    val source: MessageSource,
    val isMentioned: Boolean,
    val isSpeech: Boolean,
    val volume: Float?,
    val isSpy: Boolean
)