package org.lain.engine.container

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.world.World

data class ContainedIn(val container: EntityId) : Component

fun World.tickContainedSystem() {
    iterate<ContainedIn> { item, (parentContainer) ->
        val parentContainerEntries = parentContainer.getComponent<Entries>()?.items ?: emptyList()
        if (!parentContainerEntries.contains(item)) {
            item.removeComponent<ContainedIn>()
        }
    }

    iterate<Entries>() { container, (entries) ->
        entries.forEach { containedItem ->
            if (containedItem.getComponent<ContainedIn>()?.container != container) {
                containedItem.setComponent(ContainedIn(container))
            }
        }
    }
}