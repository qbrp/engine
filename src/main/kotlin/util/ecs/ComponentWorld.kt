package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.*
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Item
import org.lain.engine.listKotlinComponentTypeEntries
import org.lain.engine.server.networkState
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.util.Storage
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

typealias EntityId = Int

class ComponentWorld(
    val thread: Thread,
    val persistentIdToEntity: ConcurrentHashMap<PersistentId, EntityId>,
    val itemStorage: Storage<PersistentId, EngineItem>,
    registerEngineKotlinComponents: Boolean = true,
    var networkedComponentChangeListener: ((EntityId, ComponentType<out Component>) -> Unit)? = null
) : MutableComponentAccess, IterationComponentAccess {
    @Volatile
    var threadRestrictionMode = true
    var entityDestroyedListener: ((EntityId, PersistentId?) -> Unit)? = null
    private val arrays = ArrayList<ComponentArray<*>>()
    private val savableArrays = ArrayList<ComponentArray<*>>()
    private val networkingArrays = ArrayList<ComponentArray<*>>()

    // Создание сущностей потокобезопасно. Добавление компонентов - нет
    private var destroyed = Collections.synchronizedList<Boolean>(mutableListOf())
    private var freeIndexes = ConcurrentLinkedQueue<EntityId>()
    private var lastIndex = AtomicInteger()
    private val entityInstantiationLock = Any()

    init {
        if (registerEngineKotlinComponents) {
            registerComponentArrays(listKotlinComponentTypeEntries())
        }
    }

    inline fun <reified T : Component> getComponentArray(): ComponentArray<T> {
        return getComponentArray(componentTypeOf(T::class))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponentArray(type: ComponentType<T>): ComponentArray<T> {
        return getComponentArray(type.castIndexed().idx) as? ComponentArray<T>
            ?: error("No component array for $type")
    }

    fun getComponentArray(idx: Int): ComponentArray<*> {
        return arrays[idx]
    }

    fun listArrays(): List<ComponentArray<*>> {
        checkOnThread()
        return arrays
    }

    fun registerComponentArrays(entries: List<Pair<IndexedComponentType<out Component>, ComponentMeta>>) {
        checkOnThread()

        val oldArrays = arrays.toList()
        val typesById = mutableSetOf<String>()
        arrays.clear()
        savableArrays.clear()
        networkingArrays.clear()

        entries
            .sortedBy { (type, _) -> type.idx }
            .forEach { (type, meta) ->
                val idx = type.idx
                if (typesById.contains(type.id)) {
                    LOGGER.warn("Список типов компонентов для регистрации содержит дубликат ${type.id}")
                    return@forEach
                }

                if (idx != arrays.size) {
                    error("Invalid component type sequence for $type: $idx must be ${arrays.size}")
                }

                val existing = oldArrays.getOrNull(idx)
                if (existing != null && existing.type.id != type.id) {
                    error("Component type index changed at $idx: ${existing.type} -> $type")
                }

                typesById.add(type.id)

                val arr = if (existing != null && existing.type == type) {
                    existing
                } else {
                    ComponentArray(idx, meta, type as IndexedComponentType<Component>)
                }
                arr.onSet = null
                arr.onRemoved = null

                arrays += arr
                if (type == componentTypeOf(PersistentIdComponent::class)) {
                    arr.onSet = { component, entity ->
                        persistentIdToEntity[(component as PersistentIdComponent).id] = entity
                    }
                    arr.onRemoved =
                        { component, entity -> persistentIdToEntity.remove((component as PersistentIdComponent).id) }
                } else if (type == componentTypeOf(Item::class)) {
                    arr.onSet = { component, entity ->
                        val persistentId = (component as Item).uuid
                        if (itemStorage.get(persistentId) != entity) {
                            itemStorage.remove(persistentId)
                            itemStorage.add(persistentId, entity)
                        }
                    }
                    arr.onRemoved = { component, entity ->
                        itemStorage.remove((component as Item).uuid)
                    }
                }

                if (meta.savable) savableArrays.add(arr)
                if (meta.networking) networkingArrays.add(arr)
            }
    }

    suspend fun <R> withoutThreadRestriction(statement: suspend ComponentWorld.() -> R): R {
        val previousMode = threadRestrictionMode
        threadRestrictionMode = false
        return try {
            statement(this)
        } finally {
            threadRestrictionMode = previousMode
        }
    }

    private fun checkOnThread() {
        if (threadRestrictionMode) {
            val currentThread = Thread.currentThread()
            check(currentThread == thread) {
                "Invalid thread: ${currentThread.name}. Operations allowed only on ${thread.name}"
            }
        }
    }

    override fun markDirty(entity: EntityId, type: ComponentType<out Component>) {
        checkOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        val array = getComponentArray(type)
        if (array.meta.networking) {
            entity.networkState().markUpdated(type.castIndexed())
            networkedComponentChangeListener?.invoke(entity, type)
        }
    }

    override fun invalidateStates(entity: EntityId) {
        throw NotImplementedError("deprecated operation")
    }

    fun getSavableComponents(entityId: EntityId): Map<ComponentType<*>, Component> {
        checkOnThread()
        val output = mutableMapOf<ComponentType<*>, Component>()
        for (arr in savableArrays) {
            val component = arr.componentOf(entityId)
            if (component != null && arr.meta.savable) {
                output[arr.type] = component
            }
        }
        return output
    }

    fun getNetworkedComponents(entityId: EntityId): List<Component> {
        checkOnThread()
        val output = mutableListOf<Component>()
        for (arr in networkingArrays) {
            val component = arr.componentOf(entityId)
            if (component != null && arr.meta.networking) {
                output += component
            }
        }
        return output
    }

    fun collect(
        filters: List<ComponentType<out Component>>,
        statement: (ComponentArray<*>) -> Boolean
    ): List<Pair<EntityId, ComponentState>> {
        checkOnThread()
        val filterArrays = filters.map { filter ->
            arrays[filter.castIndexed().idx] ?: error("No component filter found for $filter")
        }
        val list = mutableListOf<Pair<EntityId, ComponentState>>()
        loop@ for (entityId in filterArrays.flatMap { it.denseEntities }.toSet()) {
            filterArrays.forEach { if (entityId !in it.denseEntities) continue@loop }
            val componentState = ComponentState()
            list += entityId to componentState
            for (array in arrays) {
                if (!statement(array)) continue
                val component = array.componentOf(entityId) ?: continue
                componentState.setComponent(array.type as ComponentType<Component>, component)
            }
        }
        return list
    }

    override fun addEntity(builder: context(WriteComponentAccess) EntityId.() -> Unit): EntityId =
        with(this) {
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
        val array = getComponentArray(componentTypeOf(PersistentIdComponent::class).castIndexed())
        val persistentId = array.componentOf(entity)?.id
        arrays.forEach { array -> removeComponent(entity, array.type) }
        synchronized(entityInstantiationLock) {
            destroyed[entity] = true
        }
        try {
            entityDestroyedListener?.invoke(entity, persistentId)
        } finally {
            freeIndexes.add(entity)
        }
    }

    // главный поток
    override fun exists(entity: EntityId): Boolean {
        //TODO: профайлер показывает здесь просадку из-за синхронизации листа destroyed
        return entity < destroyed.size && !destroyed[entity]
    }

    private inline fun forEachComponent(
        entity: EntityId,
        action: (ComponentArray<*>, Component) -> Unit
    ) {
        arrays.forEach { array ->
            array.componentOf(entity)?.let {
                action(array, it)
            }
        }
    }

    override fun getComponents(entity: EntityId): List<Component> {
        checkOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }

        val result = mutableListOf<Component>()
        forEachComponent(entity) { _, component ->
            result += component
        }
        return result
    }

    fun getComponentsMap(entity: EntityId): Map<IndexedComponentType<out Component>, Component> {
        checkOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }

        val result = mutableMapOf<IndexedComponentType<out Component>, Component>()
        forEachComponent(entity) { array, component ->
            result[array.type] = component
        }
        return result
    }

    override fun <T : Component> setComponentWithType(
        entity: EntityId,
        component: T,
        type: ComponentType<T>
    ) {
        checkOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        val array = getComponentArray(type)
        array.setComponent(entity, component)
        if (array.meta.networking) {
            entity.networkState().markUpdated(type.castIndexed())
            networkedComponentChangeListener?.invoke(entity, type)
        }
    }

    override fun hasComponent(
        entity: EntityId,
        type: ComponentType<out Component>
    ): Boolean {
        checkOnThread()
        return getComponentArray(type).componentOf(entity) != null
    }

    override fun <T : Component> removeComponent(entity: EntityId, type: ComponentType<T>): T? {
        checkOnThread()
        require(exists(entity)) { "Entity $entity does not exist" }
        val array = getComponentArray(type)
        val removed = array.removeComponent(entity) ?: return null
        if (array.meta.networking) {
            entity.networkState().markRemoved(type.castIndexed())
            networkedComponentChangeListener?.invoke(entity, type)
        }
        return removed
    }

    override fun <T : Component> getComponent(entity: EntityId, type: ComponentType<T>): T? {
        checkOnThread()
        return getComponentArray(type).componentOf(entity)
    }

    fun <T : Component> iterate(
        types: List<ComponentType<T>>,
        statement: MutableComponentAccess.(mutableCollection: Collection<T>, entity: EntityId) -> Unit
    ) {
        checkOnThread()
        require(types.isNotEmpty()) { "Component query must not be empty" }

        val arrays = types.map { getComponentArray(it) }
        val smallerArr = arrays.minBy { it.components.size }
        val componentsList = mutableListOf<T>()
        loop@ for (i in smallerArr.denseEntities.indices.reversed()) {
            val entity = smallerArr.denseEntities[i]
            componentsList.clear()
            arrays.forEach {
                val component = it.componentOf(entity)
                if (component != null) {
                    componentsList += component
                } else {
                    continue@loop
                }
            }
            statement(componentsList, entity)
        }
    }

    override fun <A : Component> iterate1(
        kclass1: ComponentType<A>,
        action: MutableComponentAccess.(EntityId, A) -> Unit
    ) {
        checkOnThread()
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
        checkOnThread()
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
        checkOnThread()
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
        checkOnThread()
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
        checkOnThread()
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

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ComponentWorld::class.java)
    }
}

