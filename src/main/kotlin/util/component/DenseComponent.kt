package org.lain.engine.util.component

import org.lain.engine.item.BulletFire
import org.lain.engine.world.Event
import org.lain.engine.world.VoxelEvent
import org.lain.engine.world.WorldSoundPlayRequest
import kotlin.reflect.KClass

typealias EntityId = Int

fun ComponentWorld.registerComponents() {
    registerComponent<VoxelEvent>()
    registerComponent<BulletFire>()
    registerComponent<WorldSoundPlayRequest>()
    registerComponent<Event>()
}

interface ComponentAccess {
    fun <A : Component> iterate1(kclass1: KClass<A>, action: ComponentAccess.(EntityId, A) -> Unit)

    fun <A : Component, B : Component> iterate2(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        action: ComponentAccess.(EntityId, A, B) -> Unit
    )

    fun <A : Component, B : Component, C : Component> iterate3(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        action: ComponentAccess.(EntityId, A, B, C) -> Unit
    )

    fun <A : Component, B : Component, C : Component, D : Component> iterate4(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        kclass4: KClass<D>,
        action: ComponentAccess.(EntityId, A, B, C, D) -> Unit
    )

    fun <A : Component, B : Component, C : Component, D : Component, E : Component> iterate5(
        kclass1: KClass<A>,
        kclass2: KClass<B>,
        kclass3: KClass<C>,
        kclass4: KClass<D>,
        kclass5: KClass<E>,
        action: ComponentAccess.(EntityId, A, B, C, D, E) -> Unit
    )

    fun setComponent(entity: EntityId, component: Component)
    fun <T : Component> setComponentWithType(entity: EntityId, component: T, kclass: KClass<T>)
    fun destroy(entity: EntityId)
}

inline fun <reified A : Component> ComponentAccess.iterate(noinline action: ComponentAccess.(EntityId, A) -> Unit) {
    iterate1(A::class, action)
}

inline fun <reified A : Component, reified B : Component> ComponentAccess.iterate(noinline action: ComponentAccess.(EntityId, A, B) -> Unit) {
    iterate2(A::class, B::class, action)
}

context(world: ComponentAccess)
inline fun <reified T : Component> EntityId.setComponent(component: T) {
    world.setComponentWithType(this, component, T::class)
}

context(world: ComponentAccess)
fun EntityId.destroy() {
    world.destroy(this)
}

class ComponentWorld : ComponentAccess {
    private val arrays = mutableMapOf<KClass<out Component>, ComponentArray<*>>()
    private var lastIndex = 0

    inline fun <reified T : Component> registerComponent() {
        registerComponent(T::class)
    }

    fun <T : Component> registerComponent(type: KClass<T>) {
        arrays[type] = ComponentArray<T>()
    }

    fun addEntity(): EntityId {
        return lastIndex++
    }

    override fun setComponent(entity: EntityId, component: Component) {
        getComponentArray(component::class as KClass<Component>).setComponent(entity, component)
    }

    override fun <T : Component> setComponentWithType(entity: EntityId, component: T, kclass: KClass<T>) {
        getComponentArray(kclass).setComponent(entity, component)
    }

    override fun destroy(entity: EntityId) {
        arrays.forEach { (_, array) -> array.removeComponent(entity) }
    }

    override fun <A : Component> iterate1(kclass1: KClass<A>, action: ComponentAccess.(EntityId, A) -> Unit) {
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
        action: ComponentAccess.(EntityId, A, B) -> Unit
    ) {
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
        action: ComponentAccess.(EntityId, A, B, C) -> Unit
    ) {
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
        action: ComponentAccess.(EntityId, A, B, C, D) -> Unit
    ) {
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
        action: ComponentAccess.(EntityId, A, B, C, D, E) -> Unit
    ) {
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
        return arrays[kclass] as? ComponentArray<T> ?: error("No component array for $kclass")
    }
}

class ComponentArray<T : Component> {
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

    fun removeComponent(entityId: EntityId) {
        val denseIndex = sparseArray.getOrNull(entityId) ?: return
        val lastIndex = denseArray.lastIndex

        if (denseIndex != lastIndex) {
            denseArray[denseIndex] = denseArray[lastIndex]
            denseEntities[denseIndex] = denseEntities[lastIndex]

            val movedEntity = denseEntities[denseIndex]
            sparseArray[movedEntity] = denseIndex
        }

        denseArray.removeAt(lastIndex)
        denseEntities.removeAt(lastIndex)
        sparseArray[entityId] = null
    }
}