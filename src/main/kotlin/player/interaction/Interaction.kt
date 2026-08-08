package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.util.DebugName
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.Location
import org.lain.engine.world.World

class InvalidActionStateException(val entity: EntityId, message: String) : Exception(message)

@Serializable
data class ActionSyncEvent(val entity: EntityId, val action: Component, val tick: Long) : Component

context(world: World)
fun EntityId.syncAction(action: Component) {
    val location = requireComponent<Location>()
    val event = ActionSyncEvent(this, action, requireComponent<PlayerInput>().tick)
    world.emitEvent(event, true)
        .apply {
            setComponent(DebugName("Action synchronization event ($action)"))
            setComponent(location)
        }
}