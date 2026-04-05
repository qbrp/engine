package org.lain.engine.util.component

import org.lain.cyberia.ecs.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

typealias EntityId = Int

class ComponentWorld(val thread: Thread) : MutableComponentAccess, IterationComponentAccess {
    private val arrays = mutableMapOf<ComponentType<out Component>, ComponentArray<*>>()
    private val arraysList = mutableListOf<ComponentArray<*>>()
    private val deltaBitMasks = mutableListOf<LongArray?>()

    // Создание сущностей потокобезопасно. Добавление компонентов - нет
    private var destroyed = Collections.synchronizedList<Boolean>(mutableListOf())
    private var freeIndexes = ConcurrentLinkedQueue<EntityId>()
    private var lastIndex = AtomicInteger()

    init {
        ComponentTypeRegistry.listEntries().forEach { (kclass, entry) -> addComponentArray(entry.type, entry.meta)  }
    }

    private fun <T : Component> addComponentArray(type: ComponentType<T>, meta: ComponentMeta) {
        assertOnThread()
        val arr = ComponentArray<T>(arrays.size, meta)
        arrays[type] = arr
        arraysList += arr
    }

    private fun assertOnThread() {
        val currentThread = Thread.currentThread()
        assert(currentThread == thread) { "Invalid thread: ${currentThread.name}. Operations allowed only on ${thread.name} thread" }
    }

    override fun markDirty(entity: EntityId, type: ComponentType<out Component>) {
        getDeltaBitMask(entity).markDirty(getComponentArray(type).idx)
    }

    override fun invalidateStates(entity: EntityId) {
        clearDirtyMask(entity)
    }

    fun clearDirtyMask(entity: EntityId) {
        val bitMask = getDeltaBitMask(entity)
        for (i in bitMask.indices) {
            bitMask[i] = 0L
        }
    }

    fun markDirtyIfBitMaskPreset(entity: EntityId, component: ComponentArray<*>) {
        getDeltaBitMaskIfPreset(entity)?.markDirty(component.idx)
    }

    private fun LongArray.markDirty(idx: Int) {
        val longIdx = bitMaskIdxOf(idx)
        val bitIdx = idx and 63
        val long = this[longIdx]
        this[longIdx] = long or (1L shl bitIdx)
    }

    fun getDeltaBitMaskIfPreset(entity: EntityId): LongArray? {
        assertOnThread()
        if (entity !in deltaBitMasks.indices) return null
        val bitMask = deltaBitMasks[entity] ?: return null
        return bitMask
    }

    fun getDeltaBitMask(entityId: EntityId): LongArray {
        assertOnThread()
        while(deltaBitMasks.size <= entityId) deltaBitMasks.add(null)
        val bitMask = deltaBitMasks[entityId] ?: createBitMask().also { deltaBitMasks[entityId] = it }
        return bitMask
    }

    // Создаем массив из Long-ов, количество - общее число типов компонентов, делённое на 64 (сколько битов держит один Long)
    private fun createBitMask() = LongArray(((arrays.size + 1) shr 6) + 1)

    private fun bitMaskIdxOf(idx: Int) = (idx shr 6)

    fun collect(
        filters: List<ComponentType<out Component>>,
        statement: (ComponentArray<*>) -> Boolean
    ): List<Pair<EntityId, ComponentState>> {
        assertOnThread()
        val filterArrays = filters.map { filter -> arrays[filter] ?: error("No component filter found for $filter") }
        val list = mutableListOf<Pair<EntityId, ComponentState>>()
        loop@ for (entityId in filterArrays.flatMap { it.denseEntities }.toSet()) {
            filterArrays.forEach { if (entityId !in it.denseEntities) continue@loop }
            val componentState = ComponentState()
            list += entityId to componentState
            for ((kclass, array) in arrays) {
                if (!statement(array)) continue
                val component = array.componentOf(entityId) ?: continue
                componentState.setComponent(kclass as KClass<Component>, component)
            }
        }
        return list
    }

    override fun addEntity(): EntityId {
        val idx = freeIndexes.poll() ?: run {
            destroyed.add(false)
            lastIndex.getAndIncrement()
        }
        destroyed[idx] = false
        return idx
    }

    override fun destroy(entity: EntityId) {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        arrays.forEach { (_, array) -> array.removeComponent(entity) }
        freeIndexes.add(entity)
        destroyed[entity] = true
    }

    override fun exists(entity: EntityId): Boolean {
        return lastIndex.get() >= entity && !destroyed[entity]
    }

    override fun addEntity(builder: context(WriteComponentAccess) EntityId.() -> Unit): EntityId = with(this) {
        val entity = addEntity()
        entity.builder()
        entity
    }

    override fun getComponents(entity: EntityId, bitMask: LongArray?): List<Component> {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        val components = mutableListOf<Component>()

        if (bitMask != null) {
            for (i in bitMask.indices) {
                var bits = bitMask[i]
                var bitIndex = 0
                while (bits != 0L) {
                    if ((bits and 1L) != 0L) {
                        val arrayIndex = i * 64 + bitIndex
                        if (arrayIndex < arraysList.size) {
                            arraysList[arrayIndex].componentOf(entity)?.let { components.add(it) }
                        }
                    }
                    bits = bits shr 1
                    bitIndex++
                }
            }
        } else {
            arraysList.forEach { it.componentOf(entity)?.let { comp -> components.add(comp) } }
        }

        return components
    }

    override fun <T : Component> setComponentWithType(entity: EntityId, component: T, type: ComponentType<T>) {
        assertOnThread()
        val array = getComponentArray(type)
        array.setComponent(entity, component)
    }

    override fun hasComponent(
        entity: EntityId,
        type: ComponentType<out Component>
    ): Boolean {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        return getComponentArray(type).componentOf(entity) != null
    }

    override fun <T : Component> removeComponent(entity: EntityId, type: ComponentType<T>): T? {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        val array = getComponentArray(type)
        return array.removeComponent(entity)
    }

    override fun <T : Component> getComponent(entity: EntityId, type: ComponentType<T>): T? {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        return getComponentArray(type).componentOf(entity)
    }

    override fun <A : Component> iterate1(kclass1: ComponentType<A>, action: MutableComponentAccess.(EntityId, A) -> Unit) {
        assertOnThread()
        val arr1 = getComponentArray(kclass1)
        for (i in arr1.denseEntities.lastIndex downTo 0) {
            val entity = arr1.denseEntities[i]
            val componentA = arr1.componentOf(entity) ?: continue
            action(entity, componentA)
        }
    }

    override fun <A : Component, B : Component> iterate2(
        kclass1: ComponentType<A>,
        kclass2: ComponentType<B>,
        action: MutableComponentAccess.(EntityId, A, B) -> Unit
    ) {
        assertOnThread()
        val arr1 = getComponentArray(kclass1)
        val arr2 = getComponentArray(kclass2)
        val smallerArr = listOf(arr1, arr2).minBy { it.components.size }

        for (i in smallerArr.denseEntities.lastIndex downTo 0) {
            val entity = smallerArr.denseEntities[i]
            val componentA = arr1.componentOf(entity) ?: continue
            val componentB = arr2.componentOf(entity) ?: continue
            action(entity, componentA, componentB)
        }
    }

    override fun <A : Component, B : Component, C : Component> iterate3(
        kclass1: ComponentType<A>,
        kclass2: ComponentType<B>,
        kclass3: ComponentType<C>,
        action: MutableComponentAccess.(EntityId, A, B, C) -> Unit
    ) {
        assertOnThread()
        val arr1 = getComponentArray(kclass1)
        val arr2 = getComponentArray(kclass2)
        val arr3 = getComponentArray(kclass3)
        val smallerArr = listOf(arr1, arr2, arr3).minBy { it.components.size }

        for (i in smallerArr.denseEntities.lastIndex downTo 0) {
            val entity = smallerArr.denseEntities[i]
            val componentA = arr1.componentOf(entity) ?: continue
            val componentB = arr2.componentOf(entity) ?: continue
            val componentC = arr3.componentOf(entity) ?: continue
            action(entity, componentA, componentB, componentC)
        }
    }

    override fun <A : Component, B : Component, C : Component, D : Component> iterate4(
        kclass1: ComponentType<A>,
        kclass2: ComponentType<B>,
        kclass3: ComponentType<C>,
        kclass4: ComponentType<D>,
        action: MutableComponentAccess.(EntityId, A, B, C, D) -> Unit
    ) {
        assertOnThread()
        val arr1 = getComponentArray(kclass1)
        val arr2 = getComponentArray(kclass2)
        val arr3 = getComponentArray(kclass3)
        val arr4 = getComponentArray(kclass4)
        val smallerArr = listOf(arr1, arr2, arr3, arr4).minBy { it.components.size }

        for (i in smallerArr.denseEntities.lastIndex downTo 0) {
            val entity = smallerArr.denseEntities[i]
            val componentA = arr1.componentOf(entity) ?: continue
            val componentB = arr2.componentOf(entity) ?: continue
            val componentC = arr3.componentOf(entity) ?: continue
            val componentD = arr4.componentOf(entity) ?: continue
            action(entity, componentA, componentB, componentC, componentD)
        }
    }

    override fun <A : Component, B : Component, C : Component, D : Component, E : Component> iterate5(
        kclass1: ComponentType<A>,
        kclass2: ComponentType<B>,
        kclass3: ComponentType<C>,
        kclass4: ComponentType<D>,
        kclass5: ComponentType<E>,
        action: MutableComponentAccess.(EntityId, A, B, C, D, E) -> Unit
    ) {
        assertOnThread()
        val arr1 = getComponentArray(kclass1)
        val arr2 = getComponentArray(kclass2)
        val arr3 = getComponentArray(kclass3)
        val arr4 = getComponentArray(kclass4)
        val arr5 = getComponentArray(kclass5)
        val smallerArr = listOf(arr1, arr2, arr3, arr4, arr5).minBy { it.components.size }

        for (i in smallerArr.denseEntities.lastIndex downTo 0) {
            val entity = smallerArr.denseEntities[i]
            val componentA = arr1.componentOf(entity) ?: continue
            val componentB = arr2.componentOf(entity) ?: continue
            val componentC = arr3.componentOf(entity) ?: continue
            val componentD = arr4.componentOf(entity) ?: continue
            val componentE = arr5.componentOf(entity) ?: continue
            action(entity, componentA, componentB, componentC, componentD, componentE)
        }
    }

    inline fun <reified T : Component> getComponentArray(): ComponentArray<T> {
        return getComponentArray(componentTypeOf(T::class))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponentArray(type: ComponentType<T>): ComponentArray<T> {
        assertOnThread()
        return arrays[type] as? ComponentArray<T> ?: error("No component array for $type")
    }
}

object Networked : Component
object Broadcast : Component

class ComponentArray<T : Component>(val idx: Int, val meta: ComponentMeta) {
    internal val sparseArray = mutableListOf<Int?>()
    internal val denseEntities = mutableListOf<EntityId>()
    internal val denseArray = mutableListOf<T>()
    val components
        get() = denseArray

    fun entityOf(componentIdx: Int) = denseEntities[componentIdx]

    fun componentOf(entityId: EntityId): T? {
        // А есть ли такая сущность вообще? Не удален ли у нее компонент?
        val sparseArrayIndex = sparseArray.getOrNull(entityId) ?: return null
        return denseArray[sparseArrayIndex]
    }

    fun setComponent(entityId: EntityId, component: T) {
        while(sparseArray.size <= entityId) sparseArray.add(null)
        val denseIndex = sparseArray[entityId] ?: run {
            denseArray.add(component)
            denseEntities.add(entityId)
            denseArray.lastIndex
        }
        denseArray[denseIndex] = component
        sparseArray[entityId] = denseIndex
    }

    fun removeComponent(entityId: EntityId): T? {
        val denseIndex = sparseArray.getOrNull(entityId) ?: return null
        val lastIndex = denseArray.lastIndex

        if (denseIndex != lastIndex) {
            denseArray[denseIndex] = denseArray[lastIndex]
            denseEntities[denseIndex] = denseEntities[lastIndex]

            val movedEntity = denseEntities[denseIndex]
            sparseArray[movedEntity] = denseIndex
        }

        val component = denseArray.removeAt(lastIndex)
        denseEntities.removeAt(lastIndex)
        sparseArray[entityId] = null
        return component
    }
}