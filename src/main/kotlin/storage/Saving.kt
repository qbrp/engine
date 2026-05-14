package org.lain.engine.storage

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.*
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.container.ContainedIn
import org.lain.engine.item.*
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.server.EngineServer
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

fun updateUnloadSystem(world: World, timers: SaveTimers) {
    val itemsTimerElapsed = timers.items.isElapsed()
    val containersTimerElapsed = timers.containers.isElapsed()
    val usedContainers = mutableSetOf<EntityId>()

    if (itemsTimerElapsed) {
        world.iterate<Item> { item, _ ->
            item.setComponent(SaveTag)
            val containedIn = item.getComponent<ContainedIn>()
            val containerUnloaded = containedIn != null && !containedIn.container.exists()
            if (!item.hasComponent<HoldsBy>() || containerUnloaded) {
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

fun Database.saveItemsBlocking(world: World): Int = with(world) {
    val itemPairsToSave = world.componentManager.collect(
        listOf(componentTypeOf(Item::class))
    ) { component -> component.meta.savable }
    val itemsToSave = itemPairsToSave.map { (_, state) ->
        EntityDto(
            state.require<PersistentId>(),
            state.getComponents().map { it.toCommonDto() }
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
            val persistentId = state.require<PersistentId>()
            if (item.hasComponent<Item>() && item.hasComponent<UnloadTag>()) {
                itemsDestroyed += persistentId
            }

            EntityDto(
                persistentId,
                state.getComponents().map { it.toCommonDto() }
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

    itemsDestroyed.forEach { server.engine.itemStorage.remove(it.value) }
    engine.handler.onItemsBatchDestroy(itemsDestroyed)

    world.iterate<SaveTag> { item, _ -> item.removeComponent<SaveTag>() }
    world.iterate<UnloadTag> { item, _ -> item.destroy() }
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
    private val componentLoadSettings = ComponentLoadSettings(null, server.namespacedStorage)
    private val commandBuffers = ConcurrentLinkedQueue<Pair<WorldId, EntityCommandBuffer>>()

    suspend fun loadWorldItem(
        uuid: PersistentId,
        world: World,
    ): EngineItem {
        server.itemStorage.getItem(uuid)?.let { item -> return item }

        // Первый блок отвечает за обработку предмета в целом. Если что-то пойдет не так - это высветится в консоль и будет выдан недействительный предмет
        val result = runCatching {
            // Функция загрузки сущности может выбрасывать ошибки, если предмет был в старой базе данных
            // Точно не знаю, с чем связано - в консоль бросается:
            // kotlinx.serialization.MissingFieldException: Field 'value' is required for type with serial name 'org.lain.engine.storage.PersistentId', but it was missing
            // (в EntityPersistent.deserializeEntityComponents)
            // Если есть исключение в базе данных - ищем предмет в старой, если и там нет, выбрасываем ошибку
            val entity = runCatching { database.loadEntity(uuid) }
            entity
                .getOrNull()?.let { components -> loadItem(world, uuid, components, componentLoadSettings) }
                ?: database.loadPersistentItemDataLegacy(uuid)?.let { (id, components) -> loadItemLegacy(components, world, id, uuid) }
                ?: run {
                    val e = entity.exceptionOrNull()
                    if (e != null) throw e
                    null
                }
        }
            .onFailure { err -> LOGGER.error("Не удалось загрузить предмет $uuid", err) }
            .getOrNull()
            ?: ItemLoadResult(server.bakeInvalidItem(world))
        val protoItem = result.protoItem
        val container = result.container?.let { database.loadEntity(it) ?: error("Контейнер $it не найден") }
        dataFixItem(protoItem, server.namespacedStorage)

        val commandBuffer = EntityCommandBuffer(world)
        container?.let { components ->
            protoItem.state.set(
                ContainedIn(
                    commandBuffer.instantiateEntity(
                        result.container,
                        components.mapNotNull { it.toDomain(componentLoadSettings) }
                    )
                )
            )
        }
        val item = commandBuffer.instantiateItem(protoItem, server.itemStorage)
        commandBuffers += world.id to commandBuffer
        return item
    }

    fun applyCommands(world: World) {
        commandBuffers.flush { (worldId, buffer) ->
            if (world.id == worldId) buffer.apply(world)
        }
    }
}