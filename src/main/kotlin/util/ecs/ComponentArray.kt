package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId

class ComponentArray<T : Component>(
    val idx: Int,
    val meta: ComponentMeta,
    val type: IndexedComponentType<T>,
    var onSet: ((T, EntityId) -> Unit)? = null,
    var onRemoved: ((T, EntityId) -> Unit)? = null
) {
    internal var sparseArray = LongArray(8) { MISSING_COMPONENT } // индекс - позиция в denseArray
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

    private fun denseArrayIdx(entityId: EntityId): Int? {
        val packedId = sparseArray.getOrNull(entityId.index) ?: return null
        if (packedId == MISSING_COMPONENT) return null
        val unpackedGeneration = (packedId ushr 32).toInt()
        val unpackedDenseArrayIdx = packedId.toInt()
        if (entityId.generation != unpackedGeneration) return null
        return unpackedDenseArrayIdx
    }

    fun hasComponent(entityId: EntityId): Boolean {
        return denseArrayIdx(entityId) != null
    }

    fun componentOf(entityId: EntityId): T? {
        return denseArray.getOrNull(denseArrayIdx(entityId) ?: return null)
    }

    fun setComponent(entityId: EntityId, component: T) {
        tryExpand(entityId)

        val lastPackedId = packedIdOf(entityId)
        val lastDenseIdx = lastPackedId?.toInt()
        val lastComponentGeneration = lastPackedId?.let { (it ushr 32).toInt() }

        // обработка replace-коллбэка
        onRemoved?.let { callback ->
            if (lastDenseIdx != null && lastComponentGeneration != null) {
                callback(
                    denseArray[lastDenseIdx],
                    entityId.takeIf { it.generation == lastComponentGeneration }
                        ?: EntityId(entityId.index, lastComponentGeneration)
                )
            }
        }

        val denseIdx = lastDenseIdx ?: run {
            denseArray.add(component)
            denseEntities.add(entityId)
            denseArray.lastIndex
        }

        denseArray[denseIdx] = component
        denseEntities[denseIdx] = entityId
        sparseArray[entityId.index] = packedId(denseIdx, entityId.generation)
        onSet?.invoke(component, entityId)
    }

    fun removeComponent(entityId: EntityId): T? {
        val packedId = packedIdOf(entityId) ?: return null
        val denseIdx = packedId.toInt()
        val generation = (packedId ushr 32).toInt()
        if (generation != entityId.generation) return null

        val lastDenseIdx = denseArray.lastIndex
        val removedComponent = denseArray[denseIdx]

        if (denseIdx != lastDenseIdx) {
            denseArray[denseIdx] = denseArray[lastDenseIdx]
            denseEntities[denseIdx] = denseEntities[lastDenseIdx]

            val movedEntity = denseEntities[denseIdx]
            sparseArray[movedEntity.index] = packedId(denseIdx, movedEntity.generation)
        }

        denseArray.removeAt(lastDenseIdx)
        denseEntities.removeAt(lastDenseIdx)
        sparseArray[entityId.index] = MISSING_COMPONENT

        onRemoved?.invoke(removedComponent, entityId)
        return removedComponent
    }

    private fun tryExpand(id: EntityId) {
        var currentSize = sparseArray.size
        var expand = false
        while(currentSize <= id.index) {
            currentSize = (currentSize * 1.7).toInt()
            expand = true
        }

        val oldSize = sparseArray.size
        if (expand) sparseArray = sparseArray.copyOf(currentSize)
        for (i in sparseArray.lastIndex downTo oldSize) {
            sparseArray[i] = MISSING_COMPONENT
        }
    }

    private fun packedId(denseIdx: Int, generation: Int): Long {
        val generationMask = generation.toLong() shl 32
        return denseIdx.toLong() or generationMask
    }

    private fun packedIdOf(entityId: EntityId) = sparseArray.getOrNull(entityId.index)
        .takeIf { it != MISSING_COMPONENT }

    companion object {
        val MISSING_COMPONENT = -1L
    }
}