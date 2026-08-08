package org.lain.engine.util.component

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType

class ComponentArray<T : Component>(
    val idx: Int,
    val meta: ComponentMeta,
    val type: ComponentType<T>,
    var onSet: ((T, EntityId) -> Unit)? = null,
    var onRemoved: ((T, EntityId) -> Unit)? = null
) {
    internal val sparseArray = mutableListOf<Int?>()
    internal val denseEntities = mutableListOf<EntityId>()
    internal val denseArray = mutableListOf<T>()
    val components
        get() = denseArray

    fun entityOf(componentIdx: Int) = denseEntities[componentIdx]

    fun getOrSet(entityId: EntityId, factory: () -> T): T {
        val component = componentOf(entityId)
        if (component != null) {
            return component
        } else {
            val newComponent = factory()
            setComponent(entityId, newComponent)
            return newComponent
        }
    }

    fun componentOf(entityId: EntityId): T? {
        // А есть ли такая сущность вообще? Не удален ли у нее компонент?
        val sparseArrayIndex = sparseArray.getOrNull(entityId) ?: return null
        return denseArray[sparseArrayIndex]
    }

    fun setComponent(entityId: EntityId, component: T) {
        while(sparseArray.size <= entityId) sparseArray.add(null)
        val existingDenseArrayIdx = sparseArray[entityId]
        if (onRemoved != null && existingDenseArrayIdx != null) {
            denseArray.getOrNull(existingDenseArrayIdx)?.let {
                onRemoved!!(it, entityId)
            }
        }
        val denseIndex = existingDenseArrayIdx ?: run {
            denseArray.add(component)
            denseEntities.add(entityId)
            denseArray.lastIndex
        }
        denseArray[denseIndex] = component
        sparseArray[entityId] = denseIndex
        onSet?.invoke(component, entityId)
    }

    fun removeComponent(entityId: EntityId): T? {
        val denseIndex = sparseArray.getOrNull(entityId) ?: return null
        val lastIndex = denseArray.lastIndex

        val removedComponent = denseArray[denseIndex]

        if (denseIndex != lastIndex) {
            denseArray[denseIndex] = denseArray[lastIndex]
            denseEntities[denseIndex] = denseEntities[lastIndex]

            val movedEntity = denseEntities[denseIndex]
            sparseArray[movedEntity] = denseIndex
        }

        denseArray.removeAt(lastIndex)
        denseEntities.removeAt(lastIndex)
        sparseArray[entityId] = null

        onRemoved?.invoke(removedComponent, entityId)
        return removedComponent
    }
}