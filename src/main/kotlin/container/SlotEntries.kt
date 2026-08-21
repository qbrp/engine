package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.world.World
import kotlin.collections.component1
import kotlin.collections.component2

@Serializable data class OccupiedSlots(val slots: MutableMap<SlotId, EngineItem>) : Component

// Назначать на сущность предмета
@Serializable data class AssignedSlot(val slot: SlotId) : Component

fun World.tickAssignedSlotSystem() {
    iterate<AssignedSlot>() { item, _ ->
        if (item.getComponent<ContainedIn>()?.container?.hasComponent<Slots>() != false) {
            item.removeComponent<AssignedSlot>()
        }
    }
    iterate<Container, OccupiedSlots>() { _, _, (occupiedSlots) ->
        occupiedSlots.forEach { (slot, item) ->
            if (item.getComponent<AssignedSlot>()?.slot?.id != slot.id) {
                item.setComponent(AssignedSlot(slot))
            }
        }
    }
}

fun World.projectContainerSlotsToEntries() {
    iterate<Container, Entries, OccupiedSlots>() { container, _, (entries), (occupiedSlots) ->
        entries.clear()
        entries.addAll(occupiedSlots.values)
    }
}