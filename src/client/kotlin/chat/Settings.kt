package org.lain.engine.client.chat

import kotlinx.serialization.Serializable
import org.lain.engine.chat.ChannelId
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings

fun ClientChatChannel.toChatBarSection() = ChatBarSection(listOf(this.id), this.name)

@Serializable
data class ChatBarConfiguration(val sections: List<ChatBarSection>)

@Serializable
data class ChatBarSection(
    val channels: List<ChannelId>,
    val name: String
)

data class ChatBarSectionState(
    val section: ChatBarSection,
    var hide: Boolean,
    var unread: Boolean,
    var mentioned: Boolean
)

@Serializable
data class ChatFormatSettings(
    val spy: String,
    val regex: ChatRegexSettings = ChatRegexSettings()
)

@Serializable
data class ChatRegexSettings(
    val replace: List<Replace> = listOf()
) {
    @Serializable
    data class Replace(
        val exp: String,
        val remove: String? = null,
        val value: String
    )
}