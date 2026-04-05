package org.lain.engine.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.item.ItemStack
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.*
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.container.ContainedIn
import org.lain.engine.container.Item
import org.lain.engine.item.*
import org.lain.engine.mc.engine
import org.lain.engine.mc.wrapEngineItemStack
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.EngineServer
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.world
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

val ItemIoCoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(4) + SupervisorJob())

private val LOGGER = LoggerFactory.getLogger("Engine Autosave")

data class SaveTimers(var items: Counter, var containers: Counter) {
    class Counter(val period: Int, var tick: Int = 0) {
        fun tick() { if (tick++ >= period) tick = 0 }
        fun isElapsed(): Boolean {
            return this.tick >= period
        }
        fun activate() {
            tick = period
        }
    }
}

object Savable : Component
object SaveTag : Component
object UnloadTag : Component

fun updateUnloadSystem(world: World, timers: SaveTimers) {
    val itemsTimerElapsed = timers.items.isElapsed()
    val containersTimerElapsed = timers.containers.isElapsed()
    val usedContainers = mutableSetOf<EntityId>()

    if (itemsTimerElapsed) {
        world.iterate<Item> { item, _ ->
            item.setComponent(SaveTag)
            val containedIn = item.getComponent<ContainedIn>()
            // Если контейнер выгружен
            if (containedIn != null && !containedIn.container.exists()) {
                item.setComponent(UnloadTag)
            }
        }
    }
    // пока похуй
//    world.iterate<Container, Savable>() { container, _, _ ->
//        if (containersTimerElapsed) container.setComponent(SaveTag)
//        if (container !in usedContainers) {
//            container.setComponent(UnloadTag)
//        }
//    }
}

fun Database.saveItems(world: World) = with(world) {
    val itemPairsToSave = world.componentManager.collect(
        listOf(componentTypeOf(Item::class))
    ) { component -> component.meta.savable }
    val itemsToSave = itemPairsToSave.map { (id, state) ->
        val engineItem = id.requireComponent<Item>().engine
        engineItem to state
    }
    val itemsPersistent = itemsToSave.map { (item, state) -> item to itemPersistentData(world, item.state, state) }
    if (itemsToSave.isNotEmpty()) {
        ItemIoCoroutineScope.launch {
            saveItemPersistentDataBatch(itemsPersistent)
        }
    }
}

fun updateSaveSystem(server: EngineMinecraftServer, world: World) = with(world) {
    val engine = server.engine
    val itemPairsToSave = world.componentManager.collect(
        listOf(
            componentTypeOf(Item::class),
            componentTypeOf(SaveTag::class)
        )
    ) { component -> component.meta.savable }
    val itemsToSave = itemPairsToSave.map { (entity, state) ->
        val engineItem = entity.requireComponent<Item>().engine
        engineItem to state
    }

    // Выгружаем
    val itemsDestroyed = itemsToSave.filter { (item, state) -> item.entity.hasComponent<UnloadTag>() }
    itemsDestroyed.forEach { destroyItem(it.first, engine.itemStorage) }
    engine.handler.onItemsBatchDestroy(itemsDestroyed.map { it.first.uuid })

    val itemsPersistent = itemsToSave.map { (item, state) -> item to itemPersistentData(world, item.state, state) }
    if (itemsToSave.isNotEmpty()) {
        ItemIoCoroutineScope.launch {
            server.database.saveItemPersistentDataBatch(itemsPersistent)
            LOGGER.info("Выгружено {} неактивных предметов, всего сохранено {}", itemsDestroyed.size, itemsToSave.size)
        }
    }

    // Сохраняем все сущности
    val entitiesToSave = (world.componentManager.collect(listOf(componentTypeOf(SaveTag::class))) { component -> component.meta.savable } - itemPairsToSave.toSet())
        .map { (_, state) -> state.require<PersistentId>().copy() to state }
    ItemIoCoroutineScope.launch {
        if (entitiesToSave.isNotEmpty()) server.database.saveEntitiesBatch(entitiesToSave)
    }

    world.iterate<SaveTag> { item, _ -> item.removeComponent<SaveTag>() }
    world.iterate<UnloadTag> { item, _ -> item.removeComponent<UnloadTag>() }
}

fun dataFixItem(item: ProtoItem, storage: NamespacedStorage) {
    val itemState = item.state
    if (!itemState.has<ItemAssets>()) {
        val prefab = storage.items[item.prefabId] ?: return
        val assets = prefab.assets
        itemState.setNullable(assets)
    }
    if (!itemState.has<ItemProgressionAnimations>()) {
        val prefab = storage.items[item.prefabId] ?: return
        val animations = prefab.progressionAnimations
        itemState.setNullable(animations)
    }
    if (!itemState.has<Count>()) {
        itemState.set(Count(1, 1))
    }
}

class ItemLoader(
    private val server: EngineServer,
    private val database: Database
) {
    private val commandBuffers = ConcurrentLinkedQueue<Pair<WorldId, EntityCommandBuffer>>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val items = mutableMapOf<ItemUuid, Long>()
    private val notFound = mutableSetOf<ItemUuid>()

    fun isLoading(item: ItemUuid) = item in items

    fun isNotFound(item: ItemUuid) = item in notFound

    suspend fun loadWorldItem(
        uuid: ItemUuid,
        world: World,
    ): EngineItem? {
        server.itemStorage.getItem(uuid)?.let { item -> return item }
        val (id, persistentItemData) = database.loadPersistentItemData(uuid) ?: run {
            notFound.add(uuid)
            return null
        }
        val result = loadItem(persistentItemData, world, id, uuid)
        val protoItem = result.protoItem
        val container = result.container?.let { database.loadEntity(it) ?: error("Контейнер $it не найден") }
        dataFixItem(protoItem, server.namespacedStorage)

        val commandBuffer = EntityCommandBuffer(world)
        container?.let { components -> protoItem.entityState?.set(ContainedIn(commandBuffer.instantiateEntity(result.container, components))) }
        val item = commandBuffer.instantiateItem(protoItem, server.itemStorage)
        commandBuffers += world.id to commandBuffer
        return item
    }

    suspend fun loadItemStack(itemStack: ItemStack, owner: EnginePlayer): EngineItem? {
        val reference = itemStack.engine() ?: return null
        val uuid = reference.uuid
        return loadWorldItem(uuid, owner.world)
    }

    fun loadItemStackWrapping(itemStack: ItemStack, owner: EnginePlayer) {
        val uuid = itemStack.engine()?.uuid ?: return
        val time = items[uuid]
        val currentTimeMillis = System.currentTimeMillis()
        if (time == null || currentTimeMillis - time > TIMEOUT) {
            items[uuid] = currentTimeMillis
            coroutineScope.launch {
                val item = loadItemStack(itemStack, owner) ?: run {
                    notFound.add(uuid)
                    return@launch
                }
                server.execute { wrapEngineItemStack(item, itemStack) }
            }
        }
    }

    fun applyCommands(world: World) {
        commandBuffers.forEach { (worldId, buffer) ->
            if (world.id == worldId) buffer.apply(world)
        }
    }

    companion object {
        private const val TIMEOUT = 10 * 1000
    }
}