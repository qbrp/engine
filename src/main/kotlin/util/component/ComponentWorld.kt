package org.lain.engine.util.component

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Item
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.util.Storage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

typealias EntityId = Int

class ComponentWorld(
    val thread: Thread,
    val persistentIdToEntity: ConcurrentHashMap<PersistentId, EntityId>,
    val itemStorage: Storage<PersistentId, EngineItem>
) : MutableComponentAccess, IterationComponentAccess {
    private val arrays = LinkedHashMap<String, ComponentArray<*>>()
    private val arraysList = ArrayList<ComponentArray<*>>()
    private val savableArrays = HashMap<String, ComponentArray<*>>()
    private val networkingArrays = HashMap<String, ComponentArray<*>>()
    private val deltaBitMasks = ArrayList<LongArray?>()

    // Создание сущностей потокобезопасно. Добавление компонентов - нет
    private var destroyed = Collections.synchronizedList<Boolean>(mutableListOf())
    private var freeIndexes = ConcurrentLinkedQueue<EntityId>()
    private var lastIndex = AtomicInteger()
    private val entityInstantiationLock = Any()

    init { invalidateComponentArrays(ComponentTypeRegistry.listEntries().map { it.value.type to it.value.meta }) }

    inline fun <reified T : Component> getComponentArray(): ComponentArray<T> {
        return getComponentArray(componentTypeOf(T::class))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponentArray(type: ComponentType<T>): ComponentArray<T> {
        return arrays[type.id] as? ComponentArray<T> ?: error("No component array for $type")
    }

    fun listArrays(): List<ComponentArray<*>> = arraysList

    fun invalidateComponentArrays(entries: List<Pair<ComponentType<out Component>, ComponentMeta>>) {
        entries.forEach { (type, meta) ->
            val id = type.id
            val existingArray = arrays[id]
            if (existingArray == null || existingArray.meta != meta) {
                networkingArrays.remove(id)
                savableArrays.remove(id)

                val arr = ComponentArray(arraysList.size, meta, type as ComponentType<Component>)
                if (type == componentTypeOf(PersistentIdComponent::class)) {
                    arr.onAdded = { component, entity -> persistentIdToEntity[(component as PersistentIdComponent).id] = entity }
                    arr.onRemoved = { component, entity -> persistentIdToEntity.remove((component as PersistentIdComponent).id) }
                } else if (type == componentTypeOf(Item::class)) {
                    arr.onAdded = { component, entity ->
                        val persistentId = (component as Item).uuid
                        if (itemStorage.get(persistentId) != entity) {
                            println("Added item $persistentId ($entity)")
                            itemStorage.remove(persistentId)
                            itemStorage.add(persistentId, entity)
                        }
                    }
                    arr.onRemoved = { component, entity ->
                        println("Removed item $component ($entity)")
                        itemStorage.remove((component as Item).uuid)
                    }
                }

                arrays[id] = arr
                arraysList += arr
                if (meta.savable) savableArrays[id] = arr
                if (meta.networking) networkingArrays[id] = arr
            }
        }
    }

    private fun assertOnThread() {
        val currentThread = Thread.currentThread()
        assert(currentThread == thread) { "Invalid thread: ${currentThread.name}. Operations allowed only on ${thread.name} thread" }
    }

    override fun markDirty(entity: EntityId, type: ComponentType<out Component>) {
        getOrCreateEmptyDeltaBitMask(entity).markDirty(getComponentArray(type).idx)
    }

    override fun invalidateStates(entity: EntityId) {
        clearDirtyMask(entity)
    }

    fun clearDirtyMask(entity: EntityId) {
        val bitMask = getNetworkedDeltaBitMask(entity) ?: return
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

    fun getNetworkedDeltaBitMask(entityId: EntityId): LongArray? {
        assertOnThread()
        if (deltaBitMasks.size <= entityId) return null
        return deltaBitMasks[entityId]
    }

    fun getOrCreateEmptyDeltaBitMask(entityId: EntityId): LongArray {
        while(deltaBitMasks.size <= entityId) deltaBitMasks.add(null)
        return getNetworkedDeltaBitMask(entityId) ?: createBitMask()
            .also { mask -> deltaBitMasks[entityId] = mask }
    }

    fun getNetworkedArrays(entityId: EntityId): List<ComponentArray<*>> {
        val output = mutableListOf<ComponentArray<*>>()
        for (arr in arraysList) {
            val component = arr.componentOf(entityId)
            if (component != null && arr.meta.networking) {
                output += arr
            }
        }
        return output
    }

    fun getSavableComponents(entityId: EntityId): List<Component> {
        val output = mutableListOf<Component>()
        for (arr in savableArrays.values) {
            val component = arr.componentOf(entityId)
            if (component != null && arr.meta.savable) {
                output += component
            }
        }
        return output
    }

    fun getNetworkedComponents(entityId: EntityId): List<Component> {
        val output = mutableListOf<Component>()
        for (arr in networkingArrays.values) {
            val component = arr.componentOf(entityId)
            if (component != null && arr.meta.networking) {
                output += component
            }
        }
        return output
    }

    // Создаем массив из Long-ов, количество - общее число типов компонентов, делённое на 64 (сколько битов держит один Long)
    private fun createBitMask() = LongArray((arrays.size + 63) shr 6)

    private fun bitMaskIdxOf(idx: Int) = (idx shr 6)

    fun collect(
        filters: List<ComponentType<out Component>>,
        statement: (ComponentArray<*>) -> Boolean
    ): List<Pair<EntityId, ComponentState>> {
        assertOnThread()
        val filterArrays = filters.map { filter -> arrays[filter.id] ?: error("No component filter found for $filter") }
        val list = mutableListOf<Pair<EntityId, ComponentState>>()
        loop@ for (entityId in filterArrays.flatMap { it.denseEntities }.toSet()) {
            filterArrays.forEach { if (entityId !in it.denseEntities) continue@loop }
            val componentState = ComponentState()
            list += entityId to componentState
            for ((id, array) in arrays) {
                if (!statement(array)) continue
                val component = array.componentOf(entityId) ?: continue
                componentState.setComponent(array.type as ComponentType<Component>, component)
            }
        }
        return list
    }

    // потокобезопасно?
    override fun addEntity(builder: context(WriteComponentAccess) EntityId.() -> Unit): EntityId = with(this) {
        val entity = addEntity()
        entity.builder()
        entity
    }

    // потокобезопасно
    override fun addEntity(): EntityId = synchronized(entityInstantiationLock) {
        val idx = freeIndexes.poll() ?: run {
            destroyed.add(false)
            lastIndex.getAndIncrement()
        }
        destroyed[idx] = false
        return idx
    }

    // главный поток
    override fun destroy(entity: EntityId) {
        require(exists(entity)) { "Entity $entity does not exist" }
        arrays.forEach { (_, array) ->
            val removedComponent = array.removeComponent(entity)
            if (removedComponent != null && removedComponent is PersistentId) {
                persistentIdToEntity.remove(removedComponent)
            }
        }
        freeIndexes.add(entity)
        destroyed[entity] = true
        if (entity < deltaBitMasks.size) {
            deltaBitMasks[entity] = null
        }
    }

    // главный поток
    override fun exists(entity: EntityId): Boolean {
        assertOnThread()
        return entity < destroyed.size && !destroyed[entity]
    }

    override fun getComponents(entity: EntityId, bitMask: LongArray?): List<Component> {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        val components = mutableListOf<Component>()

        if (bitMask != null) {
            for (i in bitMask.indices) {
                var bits = bitMask[i]
                while (bits != 0L) {
                    val bitIndex = java.lang.Long.numberOfTrailingZeros(bits)
                    val arrayIndex = i * 64 + bitIndex

                    if (arrayIndex < arraysList.size) {
                        arraysList[arrayIndex].componentOf(entity)?.let { components.add(it) }
                    }

                    bits = bits and (bits - 1)
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
        for (i in arr1.denseEntities.indices.reversed()) {
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

        for (i in smallerArr.denseEntities.indices.reversed()) {
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

        for (i in smallerArr.denseEntities.indices.reversed()) {
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

        for (i in smallerArr.denseEntities.indices.reversed()) {
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

        for (i in smallerArr.denseEntities.indices.reversed()) {
            val entity = smallerArr.denseEntities[i]
            val componentA = arr1.componentOf(entity) ?: continue
            val componentB = arr2.componentOf(entity) ?: continue
            val componentC = arr3.componentOf(entity) ?: continue
            val componentD = arr4.componentOf(entity) ?: continue
            val componentE = arr5.componentOf(entity) ?: continue
            action(entity, componentA, componentB, componentC, componentD, componentE)
        }
    }
}

@Serializable
object Networked : Component

class ComponentArray<T : Component>(
    val idx: Int,
    val meta: ComponentMeta,
    val type: ComponentType<T>,
    var onAdded: ((T, EntityId) -> Unit)? = null,
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
        val denseIndex = sparseArray[entityId] ?: run {
            denseArray.add(component)
            denseEntities.add(entityId)
            denseArray.lastIndex
        }
        denseArray[denseIndex] = component
        sparseArray[entityId] = denseIndex
        onAdded?.invoke(component, entityId)
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