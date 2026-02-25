package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.server.markDirty
import org.lain.engine.util.Component
import org.lain.engine.util.apply
import org.lain.engine.util.nextId
import org.lain.engine.util.require
import kotlin.math.max

/**
 * # Нарративные уведомления
 * Отличаются от системных тем, что могут быть более массовыми и располагаются посередине экрана.
 * Могут почти не иметь содержание. Основная цель - передать мысли и чувства игрового персонажа
 */
@Serializable
data class Narration(val messages: MutableList<NarrationMessage>) : Component {
    fun get(id: Long) = messages.find { it.id == id }
}

@Serializable
data class NarrationContent(
    val text: String,
    val duration: Int
)

@Serializable
data class NarrationMessage(val content: NarrationContent, var time: Int, val id: Long = nextId())

fun EnginePlayer.narration(message: String, time: Int) = this.apply<Narration>() {
    val identical = messages.find { it.content.text == message }
    if (identical != null) {
        identical.time = max(0, identical.time - time)
    } else {
        messages += NarrationMessage(NarrationContent(message, time), 0)
    }
}

fun EnginePlayer.serverNarration(message: String, time: Int) {
    narration(message, time)
    markDirty<Narration>()
}

fun tickNarrations(player: EnginePlayer) {
    player.require<Narration>().messages.removeIf {
        it.time++ >= it.content.duration
    }
}