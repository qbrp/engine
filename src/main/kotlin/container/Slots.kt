package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Item
import org.lain.engine.item.ItemStorage
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.component.ComponentState
import org.lain.engine.world.Location
import org.lain.engine.world.World

// Назначать на сущность контейнера
@Serializable data class Slots(val available: Set<SlotId>) : Component
@Serializable data class OccupiedSlots(val slots: MutableSet<SlotId>) : Component

// Назначать на сущность предмета
@Serializable data class AssignedSlot(val slot: SlotId) : ItemComponent

// Операция
data class AssignSlot(val item: EngineItem, val slot: SlotId) : Component

@JvmInline
@Serializable
value class SlotId(val id: String) {
    override fun toString(): String = id
}

fun WriteComponentAccess.createSlotContainer(
    location: Location,
    slots: Set<SlotId>,
    networked: Boolean = false,
    items: Map<SlotId, EngineItem> = mapOf(),
    persistentId: PersistentId? = null
): EntityId {
    val occupiedSlots: MutableSet<SlotId> = mutableSetOf()
    val entries = mutableListOf<EngineItem>()
    val state = ComponentState {
        set(Slots(slots))
        set(OccupiedSlots(occupiedSlots))
    }
    val container = createContainer(location, state, persistentId, networked, entries)
    items.forEach { (slot, item) ->
        occupiedSlots += slot
        entries += item
        item.setComponent(ContainedIn(container))
        item.setComponent(AssignedSlot(slot))
    }
    return container
}

fun World.getContainerSlots(container: EntityId): Map<SlotId, EngineItem> {
    val items = mutableMapOf<SlotId, EngineItem>()
    iterate<Item, ContainedIn, AssignedSlot> { item, _, (containedId), (attachedSlot) ->
        if (containedId == container) items[attachedSlot] = item
    }
    return items
}

fun World.isSlotContainerFull(container: EntityId): Boolean {
    val slots = container.getComponent<Slots>() ?: error("Not slot container")
    val occupiedSlots = container.getComponent<OccupiedSlots>() ?: error("Not slot container")
    return occupiedSlots == slots
}

fun updateSlotContainers(world: World) {
    world.iterate<Container, Slots, OccupiedSlots, AssignSlot>() { container, _, (slots), (occupiedSlots), (itemToAttach, slotToAttach) ->
        if (slotToAttach !in slots) error("Слот $slotToAttach не существует в контейнере $container")
        container.removeComponent<AssignSlot>()
        if (slotToAttach !in occupiedSlots) {
            container.setComponent(AssignItem(itemToAttach.requireComponent<PersistentId>()))

            occupiedSlots += slotToAttach
            itemToAttach.setComponent(AssignedSlot(slotToAttach))
            itemToAttach.markDirty<AssignedSlot>()
            container.markDirty<OccupiedSlots>()
        } else {
            world.emitEvent(ContainerError("Слот $slotToAttach занят"))
        }
    }
}

fun detachSlotContainers(world: World) {
    world.iterate<Container, OccupiedSlots, DetachItem>() { container, _, (occupiedSlots), (detachedItem) ->
        val slot = detachedItem.removeComponent<AssignedSlot>()?.slot ?: return@iterate
        occupiedSlots.remove(slot)
    }
}

context(world: World)
fun updateContainerSystems(itemStorage: ItemStorage) {
    updateSlotContainers(world)
    updateContainerOperationSystem(itemStorage)
    detachSlotContainers(world)
}

context(world: World)
fun postUpdateContainerSystems() {
    updatePlayerContainerSystem()
    clearAssignItemsOperations(world)
}