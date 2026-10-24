package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.world.World
import org.lain.engine.world.location
import org.lain.engine.world.pos
import kotlin.math.max

@Serializable
data class NarrationMessage(
    val text: String,
    val duration: Int,
    val kick: Boolean,
)

@Serializable
data class NarrationEvent(
    val player: PlayerId,
    val message: NarrationMessage,
) : Component

fun EnginePlayer.narration(message: String, time: Int, kick: Boolean = false) {
    world.emitEvent(
        NarrationEvent(id, NarrationMessage(message, time, kick)),
        position = pos,
        networked = true
    )
}