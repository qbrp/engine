package org.lain.engine.util.component

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.engine.world.World

class EntityCommandBuffer(
    private val world: World,
    private val commands: MutableList<(WriteComponentAccess) -> Unit> = mutableListOf(),
) : WriteComponentAccess {

    override fun <T : Component> removeComponent(
        entity: EntityId,
        type: ComponentType<T>
    ): T? {
        commands += { world -> world.removeComponent(entity, type) }
        return null
    }

    override fun <T : Component> setComponentWithType(
        entity: EntityId,
        component: T,
        type: ComponentType<T>
    ) {
        commands += { world -> world.setComponentWithType(entity, component, type) }
    }

    override fun destroy(entity: EntityId) {
        commands += { world -> world.destroy(entity) }
    }

    override fun markDirty(
        entity: EntityId,
        type: ComponentType<out Component>
    ) {
        commands += { world -> world.markDirty(entity, type) }
    }

    override fun invalidateStates(entity: EntityId) {
        commands += { world -> world.invalidateStates(entity) }
    }

    override fun addEntity(
        builder: context(WriteComponentAccess) EntityId.() -> Unit
    ): EntityId {
        val entity = world.addEntity()
        commands += { world ->
            with(world) { builder(entity) }
        }
        return entity
    }

    fun apply(world: WriteComponentAccess) {
        commands.forEach { it(world) }
        commands.clear()
    }

    fun isEmpty(): Boolean = commands.isEmpty()
}