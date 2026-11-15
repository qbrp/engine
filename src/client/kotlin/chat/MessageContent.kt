package org.lain.engine.client.chat

import org.lain.engine.chat.MessageSource
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.client.render.ui.chat.ChatState

sealed interface MessageContent {
    fun resolve(state: ChatState): MessageText
    fun chatBubbleText(): String
}

class SpeechContent(
    distorted: MiniMessageText,
    undistorted: MiniMessageText,
    source: MessageSource,
    val volume: Float,
    val voiceProperties: VoiceProperties = VoiceProperties(volume = volume),
) : MessageContent {
    val distortedWithoutSeparator = distorted
    val senderName = source.player?.displayNameMiniMessage ?: source.author.name
    val indent = senderName.parseMiniMessageClient().string.count() + 3
    val distorted = distorted.withSeparator()
    val undistorted = undistorted.withSeparator()

    fun MiniMessageText.withSeparator() = MiniMessageText("$senderName <gray>▸</gray> ${this.text}")

    override fun resolve(state: ChatState): MessageText {
        return when(state.allhear) {
            true -> undistorted
            false -> distorted
        }
    }

    override fun chatBubbleText(): String {
        return distortedWithoutSeparator.text
    }
}

class ServiceContent(val text: MessageText) : MessageContent {
    override fun resolve(state: ChatState): MessageText = text
    override fun chatBubbleText(): String {
        return text.contentString()
    }
}
