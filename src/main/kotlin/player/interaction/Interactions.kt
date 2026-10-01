package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.player.PlayerId
import org.lain.engine.world.World

@Serializable
data class InteractionExecution(val interactionId: InteractionId) : Component

@Serializable
data class InteractionId(
    val source: PlayerId,
    val inputTick: Long,
)

fun World.afterInteractionReplication() {
    iterate<InteractionExecution> { interactor, _ -> interactor.removeComponent<InteractionExecution>() }
    iterate<InteractionInterrupt> { interactor, _ -> interactor.removeComponent<InteractionInterrupt>() }
}