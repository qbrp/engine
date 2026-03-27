package org.lain.engine.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.item.ItemStack
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.container.ContainedIn
import org.lain.engine.container.Item
import org.lain.engine.item.*
import org.lain.engine.mc.engine
import org.lain.engine.mc.wrapEngineItemStack
import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.component.*
import org.lain.engine.world.World
import org.lain.engine.world.location
import org.lain.engine.world.world
import org.slf4j.LoggerFactory

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
        listOf(Item::class)
    ) { component -> component.isSavable }
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
            Item::class,
            SaveTag::class
        )
    ) { component -> component.isSavable }
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
    val entitiesToSave = (world.componentManager.collect(listOf(SaveTag::class)) { component -> component.isSavable } - itemPairsToSave.toSet())
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

suspend fun EngineMinecraftServer.loadItemStack(itemStack: ItemStack, owner: EnginePlayer): EngineItem? {
    val reference = itemStack.engine() ?: return null
    val uuid = reference.uuid
    return database.loadWorldItem(uuid, owner, engine.namespacedStorage, engine.itemStorage)
}

suspend fun Database.loadWorldItem(
    uuid: ItemUuid,
    owner: EnginePlayer,
    contents: NamespacedStorage,
    itemStorage: ItemStorage
): EngineItem? {
    itemStorage.getItem(uuid)?.let { item -> return item }
    val (id, persistentItemData) = loadPersistentItemData(uuid) ?: return null
    val world = owner.world
    val result = loadItem(persistentItemData, owner.location, id, uuid)
    val protoItem = result.protoItem
    val containers = result.containers
        .map { world.instantiateEntity(it, loadEntity(it) ?: error("Контейнер $it не найден") ) }
    dataFixItem(protoItem, contents)
    containers.forEach { container -> protoItem.entityState?.set(ContainedIn(container)) }
    return instantiateItem(protoItem, itemStorage)
}

class ItemLoader(private val server: EngineMinecraftServer) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val items = mutableMapOf<ItemUuid, Long>()
    private val notFound = mutableSetOf<ItemUuid>()

    fun isLoading(item: ItemUuid) = item in items

    fun isNotFound(item: ItemUuid) = item in notFound

    fun loadItemStack(itemStack: ItemStack, owner: EnginePlayer) {
        val uuid = itemStack.engine()?.uuid ?: return
        val time = items[uuid]
        val currentTimeMillis = System.currentTimeMillis()
        if (time == null || currentTimeMillis - time > TIMEOUT) {
            items[uuid] = currentTimeMillis
            coroutineScope.launch {
                val item = server.loadItemStack(itemStack, owner) ?: run {
                    notFound.add(uuid)
                    return@launch
                }
                server.engine.execute { wrapEngineItemStack(item, itemStack) }
            }
        }
    }

    companion object {
        private const val TIMEOUT = 10 * 1000
    }
}