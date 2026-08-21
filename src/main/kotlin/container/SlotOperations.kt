package org.lain.engine.container

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.markDirty
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.world.World

fun World.validateSlotOperation() {
    iterate<TransferOperation, AssignSlot, Unresolved>() { operation, (_, container), (slotToAttach), _ ->
        val (slots) = container.requireComponent<Slots>()
        val (occupiedSlots) = container.requireComponent<OccupiedSlots>()

        if (slotToAttach !in slots) {
            operation.setComponent(
                Rejected("Слот $slotToAttach не существует в контейнере $container")
            )
        } else if (slotToAttach in occupiedSlots) {
            operation.setComponent(
                Rejected("Слот $slotToAttach занят")
            )
        } else {
            operation.removeComponent<Unresolved>()
        }
    }
}

fun World.setupTransferOperationSlotDetach() {
    // если предмет был назначен в слотовом контейнере - маркируем операцию
    iterate<TransferOperation, MoveFrom, Approved> { operation, (itemToAttach, _), (oldContainer), _ ->
        if (oldContainer.hasComponent<Slots>()) {
            val slot = itemToAttach.getComponent<AssignedSlot>()?.slot!!
            operation.setComponent(DetachedSlot(slot))
        }
    }
}

fun World.tickSlotContainerTransformOperationSystem() {
    // назначаем новый слот при AssignSlot, даже если предмет перемещен внутри того же контейнера
    iterate<TransferOperation, AssignSlot, Approved>() { _, (itemToAttach, container), (slotToAttach), _ ->
        val (occupiedSlots) = container.requireComponent<OccupiedSlots>()
        occupiedSlots[slotToAttach] = itemToAttach
        container.markDirty<OccupiedSlots>()
    }

    // убираем предмет из старого слота
    iterate<TransferOperation, MoveFrom, DetachedSlot, Approved>() { _, (item, container), (oldContainer), (oldSlot), _ ->
        val (occupiedSlots) = oldContainer.requireComponent<OccupiedSlots>()
        if (occupiedSlots.remove(oldSlot) == item) {
            oldContainer.markDirty<OccupiedSlots>()
        }
    }
}