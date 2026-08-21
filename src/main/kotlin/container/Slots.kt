package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.EngineItem
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.component.ComponentState
import org.lain.engine.world.Location
import org.lain.engine.world.World

// Назначать на сущность контейнера
@Serializable data class Slots(val available: Set<SlotId>) : Component

// Операция
data class AssignSlot(val slot: SlotId) : Component
data class DetachedSlot(val slot: SlotId) : Component

@JvmInline
@Serializable
value class SlotId(val id: String) {
    override fun toString(): String = id
}

fun WriteComponentAccess.createSlotContainer(
    location: Location,
    slots: Set<SlotId>,
    items: Map<SlotId, EngineItem> = mapOf(),
    networked: Boolean = false,
    persistentId: PersistentId? = null
): ContainerEntity {
    val state = ComponentState {
        set(Slots(slots))
        set(OccupiedSlots(items.toMutableMap()))
    }
    val container = createContainer(location, state, persistentId, networked)
    return container
}