package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.world.World
import kotlin.math.max

@Serializable
data class Narration(
    val messages: MutableList<NarrationMessage>,
    var revision: Long = messages.maxOfOrNull { it.id } ?: 0,
) : Component {
    fun get(id: Long) = messages.find { it.id == id }

    fun addMessage(text: String, duration: Int, kick: Boolean = false) {
        val identical = messages.find { it.content.text == text }
        if (identical != null) {
            identical.time = max(0, identical.time - duration)
        } else {
            messages += NarrationMessage(
                NarrationContent(text, duration),
                time = 0,
                kick = kick,
                id = ++revision,
            )
        }
    }
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
    val id: Long,
)

fun EnginePlayer.narration(message: String, time: Int, kick: Boolean = false) = this.apply<Narration>() {
    addMessage(message, time, kick)
    markUpdated<Narration>()
}

fun World.tickNarrationSystem() {
    iterate<Narration>() { _, (messages) ->
        messages.removeIf {
            it.time++ >= it.content.duration
        }
    }
}
