package org.lain.engine.server.replication

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.MutableComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.ecs.IndexedComponentType
import org.lain.engine.world.World
import java.util.BitSet

data class Changes(
    var revision: Long = 0,
    var changed: Boolean = false,
    val updated: BitSet = BitSet(ComponentTypeRegistry.count),
    val removed: BitSet = BitSet(ComponentTypeRegistry.count)
    //TODO: скорее всего сразу после создания BitSet-ов те будут увеличиваться, т.к.
    //после компиляции скриптов общее количество типов компонентов измениться, а здесь
    //же учитываюся только встроенные. Сделать учёт Lua-компонентов
) : Component {
    fun markUpdated(type: IndexedComponentType<out Component>): Changes {
        changed = true
        removed.clear(type.idx)
        updated.set(type.idx)
        return this
    }

    inline fun <reified T : Component> markUpdated(): Changes =
        markUpdated(ComponentTypeRegistry.componentTypeOf(T::class))

    fun markRemoved(type: IndexedComponentType<out Component>): Changes {
        changed = true
        removed.set(type.idx)
        updated.clear(type.idx)
        return this
    }

    inline fun <reified T : Component> markRemoved(): Changes =
        markRemoved(ComponentTypeRegistry.componentTypeOf(T::class))

    fun clear() {
        updated.clear()
        removed.clear()
        changed = false
    }
}

context(world: World)
inline fun <reified T : Component> EntityId.markUpdated() {
    world.componentManager.markDirty(this, ComponentTypeRegistry.componentTypeOf(T::class))
}

context(world: World)
inline fun <reified T : Component> EntityId.markRemoved() {
    val type = ComponentTypeRegistry.componentTypeOf(T::class)
    networkState().markRemoved(type)
    world.componentManager.networkedComponentChangeListener?.invoke(this, type)
}

context(world: MutableComponentAccess)
fun EntityId.networkState() = getComponent<Changes>() ?: run {
    val state = Changes()
    setComponent(state)
    state
}

inline fun BitSet.forEachIndex(action: (Int) -> Unit) {
    var index = nextSetBit(0)
    while (index >= 0) {
        action(index)
        index = nextSetBit(index + 1)
    }
}

context(world: World)
fun Changes.collectUpdates(entityId: org.lain.cyberia.ecs.EntityId): List<Component> {
    val updates = mutableListOf<Component>()
    updated.forEachIndex { arrayId ->
        world.componentManager.getComponentArray(arrayId).componentOf(entityId)
            ?.let { component -> updates += component }
    }
    return updates
}

context(world: World)
fun Changes.collectRemoves(): List<ComponentType<*>> {
    val removes = mutableListOf<ComponentType<*>>()
    removed.forEachIndex { arrayId ->
        removes += world.componentManager.getComponentArray(arrayId).type
    }
    return removes
}
