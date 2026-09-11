package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.util.nextIdFast
import org.lain.engine.world.World
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
data class NarrationMessage(
    val content: NarrationContent,
    var time: Int,
    val kick: Boolean,
    val id: Long = nextIdFast()
)

fun EnginePlayer.narration(message: String, time: Int, kick: Boolean = false) = this.apply<Narration>() {
    val identical = messages.find { it.content.text == message }
    if (identical != null) {
        identical.time = max(0, identical.time - time)
    } else {
        messages += NarrationMessage(NarrationContent(message, time), 0, kick)
    }
    markUpdated<Narration>()
}

fun EnginePlayer.serverNarration(message: String, time: Int, kick: Boolean = false) {
    narration(message, time, kick)
}

fun World.tickNarrationSystem() {
    iterate<Narration>() { _, (messages) ->
        messages.removeIf {
            it.time++ >= it.content.duration
        }
    }
}