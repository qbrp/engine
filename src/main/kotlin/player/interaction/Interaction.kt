package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerId
import org.lain.engine.util.DebugName
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.Location
import org.lain.engine.world.World

class InvalidActionStateException(val entity: EntityId, message: String) : Exception(message)

@Serializable
data class InteractionId(
    val source: PlayerId,
    val inputTick: Long,
)

data class ActionExecution(
    val interactionId: InteractionId,
) : Component

@Serializable
data class ActionSyncEvent(
    val entity: EntityId,
    val action: Component,
    val interactionId: InteractionId,
) : Component {
    val tick: Long
        get() = interactionId.inputTick
}

context(world: World)
fun EntityId.syncAction(action: Component) {
    val location = requireComponent<Location>()
    val execution = removeComponent<ActionExecution>() ?: run {
        val player = requireComponent<PlayerComponent>().obj
        ActionExecution(
            InteractionId(player.id, requireComponent<PlayerInput>().tick),
        )
    }
    val event = ActionSyncEvent(
        this,
        action,
        execution.interactionId,
    )
    world.emitEvent(event, true)
        .apply {
            setComponent(DebugName("Action synchronization event ($action)"))
            setComponent(location)
        }
}
