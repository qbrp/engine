package org.lain.engine.container

import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.markDirty
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.world.World

// Фаза операций БАЗОВОГО контейнера.
// Контейнер без дополнительных компонентов является базовым, т.е. имеет бесконечное вместилище
// предметов и представляет из себя их линейный список.
// Компоненты других типов контейнера (например, слотового) имеют контроль над Entries и перезаписывают

fun World.tickBaseContainerTrasnformOperationSystem() {
    // удаляем предмет из старого контейнера
    iterate<TransferOperation, MoveFrom, Approved>() { _, (itemToAttach, container), (oldContainer), _ ->
        oldContainer.requireComponent<Entries>().items -= itemToAttach
        oldContainer.markDirty<Entries>()
    }

    // перемещаем предмет в новый контейнер, всегда
    iterate<TransferOperation, Approved>() { _, (itemToAttach, container), _ ->
        container.requireComponent<Entries>().items += itemToAttach
        container.markDirty<Entries>()
    }
}