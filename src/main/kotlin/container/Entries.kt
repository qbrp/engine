package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.item.EngineItem

@Serializable
data class Entries(val items: MutableSet<EngineItem>) : Component

object EntriesDirty : Component

context(access: ReadComponentAccess)
fun ContainerEntity.getContainerItems(): Set<EngineItem> {
    return entity.requireComponent<Entries>().items
}

context(access: ReadComponentAccess)
fun ContainerEntity.collectEntriesRecursive(): List<EngineItem> {
    val visited = mutableSetOf<EntityId>()
    val result = mutableListOf<EngineItem>()
    fun visit(container: EntityId) {
        if (!visited.add(container)) return
        val (entries) = container.getComponent<Entries>() ?: return

        for (child in entries) {
            result += child
            val anchor = child.getComponent<HasContainer>() ?: continue
            visit(anchor.container)
        }
    }
    visit(entity)
    return result
}