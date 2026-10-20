package org.lain.engine.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.*
import org.lain.engine.container.ContainedIn
import org.lain.engine.item.Count
import org.lain.engine.item.EngineItem
import org.lain.engine.item.HeldBy
import org.lain.engine.item.Item
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.util.ecs.ComponentState
import org.lain.engine.util.ecs.componentType
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

val StorageCoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(4) + SupervisorJob())

internal val LOGGER = LoggerFactory.getLogger("Engine Storage")

data class SaveTimers(
    var items: Counter, var containers: Counter
) {
    class Counter(val period: Int, var tick: Int = 0) {
        fun tick() {
            if (tick++ >= period) tick = 0
        }

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

data class UnloadComponent(val handle: EntityUnload) : Component

fun World.tickUnloadSystem(coordinator: EntityCoordinator, timers: SaveTimers) {
    val itemsTimerElapsed = timers.items.isElapsed()

    if (itemsTimerElapsed) {
        val entitiesToUnload = mutableListOf<EntityId>()
        iterate<Item> { item, _ ->
            item.setComponent(SaveTag)
            val containedIn = item.getComponent<ContainedIn>()
            val containerUnloaded = containedIn != null && !containedIn.container.exists()
            if (item.getComponent<HeldBy>()?.owner == null || containerUnloaded) {
                entitiesToUnload += item
            }
        }
        entitiesToUnload.forEach { entity ->
            val persistentId = entity.requireComponent<PersistentIdComponent>().id
            val handle = coordinator.tryUnloadEntity(this, persistentId)
            if (handle != null) {
                entity.setComponent(UnloadComponent(handle))
            }
        }
    }
}

data class EntitySnapshot(
    val uuid: PersistentId,
    val persistenceData: EntityPersistenceData,
    val components: List<SavingComponentSnapshot>,
)

data class SavingComponentSnapshot(
    val snapshot: ComponentSnapshot,
    val type: ComponentType<*>
) {
    fun serializeToRecord() = ComponentPersistentRecord(
        type.id.asRawEngineId(),
        (type as? ScriptComponentType)?.version ?: 0,
        snapshot.serializeToComponentPayload(),
        null
    )
}

data class WorldSaveSnapshot(
    val items: List<EntitySnapshot>
) {
    val size = items.size

    fun isEmpty() = items.isEmpty()
}

context(world: World)
fun EngineItem.saveSnapshot(
    state: ComponentState = ComponentState(world.componentManager.getComponents(this)),
    item: Item = requireComponent(),
    count: Count = requireComponent()
) =
    EntitySnapshot(
        persistentId(),
        EntityPersistenceData.Item(
            item.id.toString().asRawEngineId(),
            count.value,
            count.max
        ),
        state.entries.map { (type, component) ->
            SavingComponentSnapshot(
                component.snapshot(),
                type
            )
        }
    )

fun World.createSaveSnapshot(
    filter: List<ComponentType<out Component>> = listOf(componentType<SaveTag>())
): WorldSaveSnapshot {
    val itemSnapshots = mutableListOf<EntitySnapshot>()

    val entitiesToSave = componentManager.collect(filter) { component -> component.meta.savable }
    entitiesToSave.forEach { (entity, state) ->
        val item = entity.getComponent<Item>()
        if (item != null) {
            itemSnapshots += entity.saveSnapshot(state, item)
        }
    }

    return WorldSaveSnapshot(itemSnapshots)
}

suspend fun Database.saveWorldSnapshot(snapshot: WorldSaveSnapshot) {
    val (items) = snapshot
    val itemComponentDtos = items.flatMap { entity ->
        entity.components.map { component ->
            ComponentBatchDto(
                entity.uuid,
                component.serializeToRecord(),
            )
        }
    }
    val itemEntities = items.map { entity ->
        EntityBatchDto(entity.uuid, EntityDatabaseKind.ITEM)
    }
    val itemPersistenceData = mutableListOf<Pair<PersistentId, EntityPersistenceData.Item>>()
    items.forEach { item ->
        when (val data = item.persistenceData) {
            is EntityPersistenceData.Item -> {
                itemPersistenceData += item.uuid to data
            }
            else -> error("Обобщенное сохранение не поддерживается для $data")
        }
    }
    if (itemEntities.isNotEmpty()) {
        saveEntitiesBatch(
            itemEntities,
            EntityPersistenceDataBatchDto(itemPersistenceData),
            itemComponentDtos, emptyList()
        )
        LOGGER.info("Сохранено ${snapshot.size} сущностей")
    }
}

//TODO: сделать разделение для разных миров
context(world: World)
fun tickSaveSystem(server: EngineMinecraftServer) {
    val snapshot = world.createSaveSnapshot()

    var destroyed = 0
    world.iterate<SaveTag> { entity, _ -> entity.removeComponent<SaveTag>() }

    val unloading = mutableListOf<Pair<EntityId, EntityUnload>>()
    world.iterate<UnloadComponent> { entity, (handle) ->
        unloading += entity to handle
    }

    StorageCoroutineScope.launch {
        server.database.saveWorldSnapshot(snapshot)
        if (!snapshot.isEmpty()) {
            server.engine.execute {
                unloading.forEach { (entity, handle) ->
                    entity.destroy()
                    server.engine.entityCoordinator.finishEntityUnload(handle)
                    destroyed++
                }
                LOGGER.info("Выгружено $destroyed сущностей")
            }
        }
    }
}
