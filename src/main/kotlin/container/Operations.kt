package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World

/**
 * # Перемещение предмета
 * В ядре системы контейнеров стоит основная операция по перемещению предмета в какой-либо контейнер.
 * Остальные системы (например контейнеров со слотами) при определенных условиях создают компонент перемещения предмета.
 * Любой предмет прикреплен к какому-либо контейнеру. Некоторые контейнеры условны (как, например, void или world)
 * @see AssignSlot
 */
data class AssignItem(val item: EngineItem, val container: EntityId) : Component
data class DetachItem(val item: EngineItem, val container: EntityId) : Component

context(world: World)
fun EntityId.emitItemAssignEvent(item: EngineItem) {
    world.emitEvent(
        AssignItem(item, this)
    )
}

context(world: World)
fun EntityId.emitItemDetachEvent(item: EngineItem) {
    world.emitEvent(
        DetachItem(item, this)
    )
}

context(world: World)
fun updateContainerOperationSystem() {
    world.iterate<AssignItem>() { _, (itemToAttach, container) ->
        val (entries) = container.requireComponent<Entries>()
        if (entries.contains(itemToAttach)) {
            error("Контейнер уже содержит ${itemToAttach.getName()}")
        }

        entries += itemToAttach
        itemToAttach.removeComponent<ContainedIn>()
            ?.let { it.container.emitItemDetachEvent(itemToAttach) }
        itemToAttach.setComponent(ContainedIn(container))
    }

    world.iterate<DetachItem> { _, (itemToDetach, container) ->
        val (entries) = container.requireComponent<Entries>()
        entries -= itemToDetach
    }
}

fun clearAssignItemsOperations(world: World) {
    world.iterate<AssignItem>() { container, _ ->
        container.removeComponent<AssignItem>()
    }
    world.iterate<DetachItem>() { container, _ ->
        container.removeComponent<DetachItem>()
    }
}

context(world: World)
fun updatePlayerContainerSystem() {
    world.iterate<Item, ContainedIn, HeldBy>() { item, _, (container), (owner) ->
        if (owner == null) return@iterate
        if (!container.hasComponent<PlayerContainerTag>()) {
            item.setComponent(DecrementItem(item.getCount()))
        } else if (item !in owner.items) {
            val inventory = owner.require<PlayerInventory>()
            val slot = if (inventory.mainHandFree) inventory.selectedSlot else null
            owner.getOrSet { GiveItemSignal(item, slot) }
            container.requireComponent<Entries>().items -= item
            item.removeComponent<ContainedIn>()
        }
    }
}