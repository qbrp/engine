package org.lain.engine.util.component

import org.lain.engine.world.World
import kotlin.reflect.KClass

interface WriteComponentAccess {
    fun <T : Component> removeComponent(entity: EntityId, type: KClass<T>): T?
    fun <T : Component> setComponentWithType(entity: EntityId, component: T, kclass: KClass<T>)
    fun destroy(entity: EntityId)
    fun markDirty(entity: EntityId, component: KClass<out Component>)
    fun invalidateStates(entity: EntityId)
    fun addEntity(builder: context(WriteComponentAccess) EntityId.() -> Unit): EntityId
    fun addEntity(): EntityId {
        return addEntity {}
    }
}

context(world: WriteComponentAccess)
fun EntityId.clearMetaState() {
    return world.invalidateStates(this)
}

context(world: WriteComponentAccess)
inline fun <reified T : Component> EntityId.setComponent(component: T) {
    setComponent(component, T::class)
}

context(world: WriteComponentAccess)
fun <T : Component> EntityId.setComponent(component: T, kClass: KClass<T>) {
    world.setComponentWithType(this, component, kClass)
}

context(world: WriteComponentAccess)
fun EntityId.copyState(componentState: ComponentState) {
    componentState.forEach { setComponent(it, it::class as KClass<Component>) }
}

context(world: WriteComponentAccess)
fun EntityId.copyState(componentState: List<Component>) {
    componentState.forEach { setComponent(it, it::class as KClass<Component>) }
}

context(world: WriteComponentAccess)
inline fun <reified T : Component> EntityId.markDirty() {
    world.markDirty(this, T::class)
}

context(world: WriteComponentAccess)
fun EntityId.destroy() {
    world.destroy(this)
}

class EntityCommandBuffer(
    private val world: World,
    private val commands: MutableList<(WriteComponentAccess) -> Unit> = mutableListOf(),
) : WriteComponentAccess {

    override fun <T : Component> removeComponent(
        entity: EntityId,
        type: KClass<T>
    ): T? {
        commands += { world -> world.removeComponent(entity, type) }
        return null
    }

    override fun <T : Component> setComponentWithType(
        entity: EntityId,
        component: T,
        kclass: KClass<T>
    ) {
        commands += { world -> world.setComponentWithType(entity, component, kclass) }
    }

    override fun destroy(entity: EntityId) {
        commands += { world -> world.destroy(entity) }
    }

    override fun markDirty(
        entity: EntityId,
        component: KClass<out Component>
    ) {
        commands += { world -> world.markDirty(entity, component) }
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