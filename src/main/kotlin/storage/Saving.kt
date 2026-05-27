package org.lain.engine.storage

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.*
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.container.ContainedIn
import org.lain.engine.item.*
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.addIfNotNull
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.util.flush
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

val StorageCoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(4) + SupervisorJob())

internal val LOGGER = LoggerFactory.getLogger("Engine Storage")

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

fun updateUnloadSystem(handler: ServerHandler, world: World, timers: SaveTimers) {
    val itemsTimerElapsed = timers.items.isElapsed()
    val containersTimerElapsed = timers.containers.isElapsed()
    val usedContainers = mutableSetOf<EntityId>()

    if (itemsTimerElapsed) {
        val unloaded = mutableListOf<PersistentId>()
        world.iterate<Item> { item, _ ->
            item.setComponent(SaveTag)
            val containedIn = item.getComponent<ContainedIn>()
            val containerUnloaded = containedIn != null && !containedIn.container.exists()
            if (!item.hasComponent<HoldsBy>() || containerUnloaded) {
                item.setComponent(UnloadTag)
                unloaded.addIfNotNull(item.getComponent<PersistentIdComponent>()?.id)
            }
        }
        if (unloaded.isNotEmpty()) {
            handler.onItemsUnload(unloaded)
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

fun Database.saveItemsBlocking(world: World): Int = with(world) {
    val itemPairsToSave = world.componentManager.collect(
        listOf(componentTypeOf(Item::class))
    ) { component -> component.meta.savable }
    val itemsToSave = itemPairsToSave.map { (_, state) ->
        EntityDto(
            state.require<PersistentIdComponent>().id,
            state.getComponents().map { it.toSnapshotDto() }
        )
    }
    runBlocking {
        saveEntitiesBatch(itemsToSave)
        itemsToSave.count()
    }
}

context(world: World)
fun updateSaveSystem(server: EngineMinecraftServer) {
    val engine = server.engine

    // Сохраняем все сущности
    // 22.04.2026: предметы сохраняются как сущности, компоненты трансформируются в toDto
    val itemsDestroyed = mutableListOf<PersistentId>()
    val entitiesToSave = world.componentManager.collect(
        listOf(componentTypeOf(SaveTag::class))
    ) { component -> component.meta.savable }
        .map { (item, state) ->
            val persistentId = state.require<PersistentIdComponent>().id
            if (item.hasComponent<Item>() && item.hasComponent<UnloadTag>()) {
                itemsDestroyed += persistentId
            }

            EntityDto(
                persistentId,
                state.getComponents().map { it.toSnapshotDto() }
            )
        }

    // защита от гонки потоков (не знаю обязательно ли)
    val itemsDestroyed2 = itemsDestroyed.toList()
    StorageCoroutineScope.launch {
        if (entitiesToSave.isNotEmpty()) {
            server.database.saveEntitiesBatch(entitiesToSave)
            engine.execute { LOGGER.info("Сохранено ${entitiesToSave.size} сущностей. Выгружено ${itemsDestroyed2.count()} предметов") }
        }
    }

    world.iterate<SaveTag> { item, _ -> item.removeComponent<SaveTag>() }
    world.iterate<UnloadTag> { item, _ -> item.destroy() }
}

context(read: ReadComponentAccess, write: WriteComponentAccess)
fun dataFixItem(item: EngineItem, storage: NamespacedStorageAccess) {
    val prefabId = item.requireComponent<Item>().id
    val prefab = storage.items[prefabId] ?: return
    if (!item.hasComponent<ItemAssets>()) {
        val assets = prefab.assets
        assets?.let { item.setComponent(it) }
    }
    if (!item.hasComponent<ItemProgressionAnimations>()) {
        val animations = prefab.progressionAnimations
        animations?.let { item.setComponent(it) }
    }
    if (!item.hasComponent<Count>()) {
        item.setComponent(Count(1, 1))
    }
    if (!item.hasComponent<ItemName>()) {
        item.setComponent(ItemName("Предмет"))
    }
}

class ItemLoader(
    private val server: EngineServer,
    private val database: Database
) {
    private val commandBuffers = ConcurrentLinkedQueue<Pair<WorldId, EntityCommandBuffer>>()

    suspend fun loadWorldItem(
        uuid: PersistentId,
        world: World,
    ): EngineItem {
        world.itemStorage.get(uuid)?.let { item -> return item }
        val commandBuffer = EntityCommandBuffer(world)
        val entityResolver = DatabaseEntityResolver(database)
        return with(commandBuffer) {
            // Первый блок отвечает за обработку предмета в целом. Если что-то пойдет не так - это высветится в консоль и будет выдан недействительный предмет
            val entity = runCatching {
                // Функция загрузки сущности может выбрасывать ошибки, если предмет был в старой базе данных
                // Точно не знаю, с чем связано - в консоль бросается:
                // kotlinx.serialization.MissingFieldException: Field 'value' is required for type with serial name 'org.lain.engine.storage.PersistentId', but it was missing
                // (в EntityPersistent.deserializeEntityComponents)
                // Если есть исключение в базе данных - ищем предмет в старой, если и там нет, выбрасываем ошибку
                val rawEntity = runCatching { database.loadEntity(uuid) }
                rawEntity
                    .getOrNull()
                    ?.let { components ->
                        // сущность автоматически добавляется в ItemStorage
                        entityResolver.loadEntity(world.componentLoadSettings, components, uuid)
                    }
                    ?: database.loadPersistentItemDataLegacy(uuid)?.let { (id, components) ->
                        // добавляем в ItemStorage вручную
                        loadItemLegacy(components, id, uuid)
                    }
                    ?: run {
                        val e = rawEntity.exceptionOrNull()
                        if (e != null) throw e
                        null
                    }
            }
                .onFailure { err -> LOGGER.error("Не удалось загрузить предмет $uuid", err) }
                .getOrNull()
                ?: server.createInvalidItem(world)
            commandBuffers += world.id to commandBuffer
            entity
        }
    }

    fun applyCommands(world: World) {
        commandBuffers.flush { (worldId, buffer) ->
            if (world.id == worldId) buffer.apply(world)
        }
    }
}