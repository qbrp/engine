package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.persistentId
import org.lain.engine.util.DebugName
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.Networked
import org.lain.engine.util.nextIdFast
import org.lain.engine.world.Location
import org.lain.engine.world.World

class InvalidActionStateException(val entity: EntityId, message: String) : Exception(message)

@Serializable
data class ActionSyncEvent(val entity: EntityId, val action: Action, val tick: Long) : Component

context(world: World)
fun EntityId.syncAction(action: Action) {
    val location = requireComponent<Location>()
    val event = ActionSyncEvent(this, action, requireComponent<PlayerInput>().tick)
    world.emitEvent(event)
        .apply {
            setComponent(DebugName("Action synchronization event ($action)"))
            setComponent(Networked)
            setComponent(location)
            setComponent(PersistentIdComponent(persistentId("event-${System.identityHashCode(event)}")))
        }
}