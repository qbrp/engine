package org.lain.engine.player

import org.lain.engine.chat.*
import org.lain.engine.util.Component
import org.lain.engine.util.flush
import org.lain.engine.util.get
import org.lain.engine.util.require
import java.util.concurrent.ConcurrentLinkedQueue

data class Speak(
    val content: String,
    val channel: ChannelId,
    val volume: Float? = null
)

data class MessageQueue(
    val messages: ConcurrentLinkedQueue<Speak> = ConcurrentLinkedQueue()
) : Component

fun EnginePlayer.speak(text: String, channel: ChannelId = ChatChannel.DEFAULT, volume: Float? = null) {
    require<MessageQueue>().messages += Speak(text, channel, volume)
}

fun EnginePlayer.flushMessages(todo: (Speak) -> Unit) {
    get<MessageQueue>()?.messages?.flush(todo)
}

fun flushPlayerMessages(
    player: EnginePlayer,
    chat: EngineChat,
    vocalSettings: VocalSettings,
) {
    player.flushMessages { message ->
        val channel = chat.getChannel(message.channel)
        val volume = message.volume ?: player.volume
        var content = message.content

        if (channel.speech) {
            //val voiceApparatus = player.require<VoiceApparatus>()
            val voiceLoosed = !player.canSpeakUnlimited

            if (voiceLoosed) {
                content = voiceLoosenContent()
            }

            if (channel.acoustic is Acoustic.Realistic) {
                val breakVoice = player.updateVoiceApparatus(chat, volume, vocalSettings)
                if (breakVoice) {
                    content = voiceBrokenContent(content, 0.5f)
                }
            }
        }

        chat.processMessage(
            IncomingMessage(
                content,
                player.volume,
                message.channel,
                MessageSource.getPlayer(player)
            )
        )
    }
}