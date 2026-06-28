package org.lain.engine.storage

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.*
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.container.ContainedIn
import org.lain.engine.item.*
import org.lain.engine.player.PlayerId
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.addIfNotNull
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.util.flush
import org.lain.engine.util.math.Pos
import org.lain.engine.world.DynamicVoxel
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

//TODO: сделать разделение для разных миров
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