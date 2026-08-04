package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemStorage
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.MovementSettings
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptEngine
import org.lain.engine.storage.ComponentLoadSettings
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.persistentId
import org.lain.engine.util.Storage
import org.lain.engine.util.component.ComponentWorld
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.Networked
import java.util.concurrent.ConcurrentHashMap

@Serializable
object Event : Component

object WorldEntity : Component

fun ComponentWorld.addWorldStateEntity(): EntityId {
    val e = addEntity()
    e.setComponent(WorldEntity)
    return e
}

class World(
    val id: WorldId,
    val players: MutableList<EnginePlayer> = mutableListOf(),
    val playersWatchingChunkProvider: EnginePlayersWatchingChunkProvider? = null,
    val isClient: Boolean = false,
    val namespacedStorage: NamespacedStorageAccess,
    val itemStorage: Storage<PersistentId, EngineItem>,
    val persistentIdToEntity: ConcurrentHashMap<PersistentId, EntityId> = ConcurrentHashMap(),
    thread: Thread,
    registerEngineKotlinComponents: Boolean = true,
    val componentManager: ComponentWorld = ComponentWorld(thread, persistentIdToEntity, itemStorage, registerEngineKotlinComponents),
    val state: EntityId = componentManager.addWorldStateEntity(),
    val scriptEngine: ScriptEngine,
) : MutableComponentAccess by componentManager, IterationComponentAccess by componentManager {
    private val scriptContext = ScriptContext.World(this)
    val componentLoadSettings = ComponentLoadSettings(itemStorage, namespacedStorage, persistentIdToEntity, scriptEngine, true)
    val chunkStorage: ChunkStorage = ChunkStorage(this, componentLoadSettings)
    var ticks = 0L


    fun tickCallbacks(callbacks: Callbacks) {
        callbacks.of(CallbackType.WORLD_TICK)?.execute(scriptContext)
        if (ticks % 20 == 0L) {
            callbacks.of(CallbackType.WORLD_TICK_20)?.execute(scriptContext)
        }
        ticks++
    }

    /**
     * Создает сущность с компонентами `event` и Event. Следует использовать как альтернативу очередям событий.
     * Последний сигнализирует о том, что сущность нужно уничтожить в конце тика
     */
    inline fun <reified T : Component> emitEvent(event: T, type: ComponentType<T>, networked: Boolean = false): EntityId {
        return componentManager.addEntity {
            setComponent(event, type)
            setComponent(Event)
            if (networked) {
                setComponent(Networked)
                setComponent(PersistentIdComponent(persistentId("event-${System.identityHashCode(event)}")))
            }
        }
    }

    inline fun <reified T : Component> emitEvent(event: T, networked: Boolean = false): EntityId {
        return emitEvent(event, componentTypeOf(T::class), networked)
    }

    fun clearEvents() {
        componentManager.iterate<Event> { entity, _ -> entity.destroy() }
    }
}

fun world(
    id: WorldId,
    thread: Thread,
    itemStorage: ItemStorage,
    namespacedStorage: NamespacedStorageAccess,
    scriptEngine: ScriptEngine,
    playersWatchingChunkProvider: EnginePlayersWatchingChunkProvider? = null
): World {
    return World(
        id,
        playersWatchingChunkProvider = playersWatchingChunkProvider,
        itemStorage = itemStorage,
        thread = thread,
        scriptEngine = scriptEngine,
        namespacedStorage = namespacedStorage
    )
}

