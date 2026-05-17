package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.world.World

/**
 * # Перемещение предмета
 * В ядре системы контейнеров стоит основная операция по перемещению предмета в какой-либо контейнер.
 * Остальные системы (например контейнеров со слотами) при определенных условиях создает компонент перемещения предмета.
 * Любой предмет прикреплен к какому-либо контейнеру. Некоторые контейнеры условны (как, например, void или world)
 * @see AssignSlot
 */
@Serializable
data class AssignItem(val PersistentId: PersistentId) : Component
data class DetachItem(val item: EngineItem) : Component

/**
 * При невозможности достижения цели операци (например, положить предмет в занятый слот) система создает событие ContainerError.
 * Событие синхронизируется по сети и обрабатывается рендером.
 */
data class ContainerError(val text: String) : Component

context(world: World)
fun updateContainerOperationSystem(itemStorage: ItemAccess) {
    world.iterate<Container, AssignItem, Entries>() { container, _, (itemToAttach), (entries) ->
        val itemToAttach = itemStorage.getItem(itemToAttach) ?: return@iterate
        if (entries.contains(itemToAttach)) {
            world.emitEvent(ContainerError("Контейнер уже содержит ${itemToAttach.getName()}"))
            return@iterate
        }

        entries += itemToAttach
        itemToAttach.removeComponent<ContainedIn>()
            ?.let {
                it.container.requireComponent<Entries>().items -= itemToAttach
                it.container.setComponent(DetachItem(itemToAttach))
            }
        itemToAttach.setComponent(ContainedIn(container))
        container.markDirty<AssignItem>()
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
    world.iterate<Item, ContainedIn, HoldsBy>() { item, _, (container), (owner) ->
        if (!container.hasComponent<PlayerContainerTag>()) {
            owner.getOrSet { DestroyItemSignal(item, item.getCount()) }
        } else if (item !in owner.items) {
            val inventory = owner.require<PlayerInventory>()
            val slot = if (inventory.mainHandFree) inventory.selectedSlot else null
            owner.getOrSet { GiveItemSignal(item, slot) }
            container.requireComponent<Entries>().items -= item
            item.removeComponent<ContainedIn>()
        }
    }
}