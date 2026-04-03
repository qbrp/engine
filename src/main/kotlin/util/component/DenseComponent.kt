package org.lain.engine.util.component

import org.lain.engine.container.*
import org.lain.engine.item.BulletFire
import org.lain.engine.item.HoldsBy
import org.lain.engine.player.PlayerContainer
import org.lain.engine.player.PlayerContainerTag
import org.lain.engine.player.PlayerEquipment
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.Savable
import org.lain.engine.storage.SaveTag
import org.lain.engine.storage.UnloadTag
import org.lain.engine.world.Event
import org.lain.engine.world.Location
import org.lain.engine.world.VoxelEvent
import org.lain.engine.world.WorldSoundPlayRequest
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

typealias EntityId = Int

fun ComponentWorld.registerComponents() {
    registerComponent<VoxelEvent>()
    registerComponent<BulletFire>()
    registerComponent<WorldSoundPlayRequest>()
    registerComponent<Event>()
    registerComponent<AssignedSlot>(isNetworking = true)
    registerComponent<OccupiedSlots>(isNetworking = true)
    registerComponent<Slots>(isNetworking = true)
    registerComponent<ContainedIn>()
    registerComponent<Entries>()
    registerComponent<PlayerEquipment>()
    registerComponent<HoldsBy>()
    registerComponent<Container>()
    registerComponent<Item>()
    registerComponent<ContainerAnchor>()
    registerComponent<AssignItem>(isNetworking = true)
    registerComponent<AssignSlot>()
    registerComponent<DetachItem>()
    registerComponent<ContainerError>()
    registerComponent<PlayerContainerTag>()
    registerComponent<Broadcast>()
    registerComponent<SaveTag>()
    registerComponent<Networked>()
    registerComponent<PlayerContainer>()
    registerComponent<UnloadTag>()
    registerComponent<Location>()
    registerComponent<PersistentId>(isSavable = true)
    registerComponent<Savable>()
}

interface ReadComponentAccess {
    fun exists(entity: EntityId): Boolean
    fun getComponents(entity: EntityId, bitMask: LongArray? = null): List<Component>
    fun hasComponent(entity: EntityId, type: KClass<out Component>): Boolean
    fun <T : Component> getComponent(entity: EntityId, type: KClass<T>): T?
}

interface IterationComponentAccess {
    fun <A : Component> iterate1(kclass1: KClass<A>, action: ReadWriteComponentAccess.(EntityId, A) -> Unit)
    fun <A : Component, B : Component> iterate2(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        action: ReadWriteComponentAccess.(EntityId, A, B) -> Unit
    )
    fun <A : Component, B : Component, C : Component> iterate3(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        action: ReadWriteComponentAccess.(EntityId, A, B, C) -> Unit
    )
    fun <A : Component, B : Component, C : Component, D : Component> iterate4(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        kclass4: KClass<D>,
        action: ReadWriteComponentAccess.(EntityId, A, B, C, D) -> Unit
    )
    fun <A : Component, B : Component, C : Component, D : Component, E : Component> iterate5(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        kclass4: KClass<D>,
        kclass5: KClass<E>,
        action: ReadWriteComponentAccess.(EntityId, A, B, C, D, E) -> Unit
    )
}

interface ReadWriteComponentAccess : ReadComponentAccess, WriteComponentAccess

inline fun <reified A : Component, R> IterationComponentAccess.collect(crossinline collector: (A) -> R): List<R> {
    val list = mutableListOf<R>()
    iterate1<A>(A::class) { _, component -> list += collector(component) }
    return list
}

inline fun <reified A : Component, reified B : Component, R> IterationComponentAccess.collect(crossinline collector: (A, B) -> R): List<R> {
    val list = mutableListOf<R>()
    iterate2(A::class, B::class) { _, componentA, componentB, -> list += collector(componentA, componentB) }
    return list
}

inline fun <reified A : Component> IterationComponentAccess.iterate(noinline action: ReadWriteComponentAccess.(EntityId, A) -> Unit) {
    iterate1(A::class, action)
}

inline fun <reified A : Component, reified B : Component> IterationComponentAccess.iterate(noinline action: ReadWriteComponentAccess.(EntityId, A, B) -> Unit) {
    iterate2(A::class, B::class, action)
}

inline fun <reified A : Component, reified B : Component, reified C : Component> IterationComponentAccess.iterate(noinline action: ReadWriteComponentAccess.(EntityId, A, B, C) -> Unit) {
    iterate3(A::class, B::class, C::class, action)
}

inline fun <reified A : Component, reified B : Component, reified C : Component, reified D : Component> IterationComponentAccess.iterate(noinline action: ReadWriteComponentAccess.(EntityId, A, B, C, D) -> Unit) {
    iterate4(A::class, B::class, C::class, D::class, action)
}

inline fun <reified A : Component, reified B : Component, reified C : Component, reified D : Component, reified E : Component> IterationComponentAccess.iterate(noinline action: ReadWriteComponentAccess.(EntityId, A, B, C, D, E) -> Unit) {
    iterate5(A::class, B::class, C::class, D::class, E::class, action)
}
context(world: ReadComponentAccess)
inline fun <reified T : Component> EntityId.hasComponent(): Boolean {
    return world.hasComponent(this, T::class)
}

context(world: ReadComponentAccess)
inline fun <reified T : Component> EntityId.getComponent(): T? {
    return world.getComponent<T>(this, T::class)
}

context(world: ReadComponentAccess)
inline fun <reified T : Component> EntityId.requireComponent(): T {
    return getComponent<T>() ?: error("Component ${T::class.simpleName} not found")
}

context(world: WriteComponentAccess)
inline fun <reified T : Component> EntityId.removeComponent(): T? {
    return world.removeComponent(this, T::class)
}

context(world: ReadComponentAccess)
fun EntityId.getAll(bitMask: LongArray? = null): List<Component> {
    return world.getComponents(this, bitMask)
}

context(world: ReadComponentAccess)
fun EntityId.exists(): Boolean {
    return world.exists(this)
}

class ComponentWorld(val thread: Thread) : ReadWriteComponentAccess, IterationComponentAccess {
    private val arrays = mutableMapOf<KClass<out Component>, ComponentArray<*>>()
    private val arraysList = mutableListOf<ComponentArray<*>>()
    private val deltaBitMasks = mutableListOf<LongArray?>()

    // Создание сущностей потокобезопасно. Добавление компонентов - нет
    private var destroyed = Collections.synchronizedList<Boolean>(mutableListOf())
    private var freeIndexes = ConcurrentLinkedQueue<EntityId>()
    private var lastIndex = AtomicInteger()

    inline fun <reified T : Component> registerComponent(isSavable: Boolean = false, isNetworking: Boolean = false) {
        registerComponent(T::class, isSavable, isNetworking)
    }

    fun <T : Component> registerComponent(type: KClass<T>, isSavable: Boolean = false, isNetworking: Boolean = false) {
        assertOnThread()
        val arr = ComponentArray<T>(arrays.size, isSavable, isNetworking)
        arrays[type] = arr
        arraysList += arr
    }

    private fun assertOnThread() {
        val currentThread = Thread.currentThread()
        assert(currentThread == thread) { "Invalid thread: ${currentThread.name}. Operations allowed only on ${thread.name} thread" }
    }

    override fun markDirty(entity: EntityId, component: KClass<out Component>) {
        getDeltaBitMask(entity).markDirty(getComponentArray(component).idx)
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
        filters: List<KClass<out Component>>,
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

    override fun <T : Component> setComponentWithType(entity: EntityId, component: T, kclass: KClass<T>) {
        assertOnThread()
        val array = getComponentArray(kclass)
        array.setComponent(entity, component)
    }

    override fun hasComponent(
        entity: EntityId,
        type: KClass<out Component>
    ): Boolean {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        return getComponentArray(type).componentOf(entity) != null
    }

    override fun <T : Component> removeComponent(entity: EntityId, type: KClass<T>): T? {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        val array = getComponentArray(type)
        return array.removeComponent(entity)
    }

    override fun <T : Component> getComponent(entity: EntityId, type: KClass<T>): T? {
        assertOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        return getComponentArray(type).componentOf(entity)
    }

    override fun <A : Component> iterate1(kclass1: KClass<A>, action: ReadWriteComponentAccess.(EntityId, A) -> Unit) {
        assertOnThread()
        val arr1 = getComponentArray(kclass1)
        for (i in arr1.denseEntities.lastIndex downTo 0) {
            val entity = arr1.denseEntities[i]
            val componentA = arr1.componentOf(entity) ?: continue
            action(entity, componentA)
        }
    }

    override fun <A : Component, B : Component> iterate2(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        action: ReadWriteComponentAccess.(EntityId, A, B) -> Unit
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
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        action: ReadWriteComponentAccess.(EntityId, A, B, C) -> Unit
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
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        kclass4: KClass<D>,
        action: ReadWriteComponentAccess.(EntityId, A, B, C, D) -> Unit
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
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        kclass4: KClass<D>,
        kclass5: KClass<E>,
        action: ReadWriteComponentAccess.(EntityId, A, B, C, D, E) -> Unit
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
        return getComponentArray(T::class)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponentArray(kclass: KClass<T>): ComponentArray<T> {
        assertOnThread()
        return arrays[kclass] as? ComponentArray<T> ?: error("No component array for $kclass")
    }
}

object Networked : Component
object Broadcast : Component

class ComponentArray<T : Component>(val idx: Int, val isSavable: Boolean = false, val isNetworking: Boolean = false) {
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