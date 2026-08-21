package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.EngineSimulation
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemStorage
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptEngine
import org.lain.engine.server.EngineServer
import org.lain.engine.storage.ComponentLoadSettings
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.persistentId
import org.lain.engine.util.Storage
import org.lain.engine.util.component.ComponentWorld
import org.lain.engine.util.component.EntityId
import org.lain.engine.server.Networked
import org.lain.engine.storage.loadWorldComponents
import org.lain.engine.util.ConcurrentStorage
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
    val simulation: EngineSimulation,
    val persistentIdToEntity: ConcurrentHashMap<PersistentId, EntityId> = ConcurrentHashMap(),
    val itemStorage: ItemStorage = ItemStorage(),
    registerEngineKotlinComponents: Boolean = true,
    val componentManager: ComponentWorld = ComponentWorld(
        simulation.thread,
        persistentIdToEntity,
        itemStorage,
        registerEngineKotlinComponents
    ),
    val players: MutableList<EnginePlayer> = mutableListOf(),
    val playersWatchingChunkProvider: EnginePlayersWatchingChunkProvider? = null,
    val server: EngineServer? = null
) : MutableComponentAccess by componentManager, IterationComponentAccess by componentManager {
    val isClient = simulation.isClient
    val state: EntityId = componentManager.addWorldStateEntity()
    val componentLoadSettings = ComponentLoadSettings(
        itemStorage,
        simulation.namespacedStorage,
        persistentIdToEntity,
        simulation.scriptEngine,
        simulation.isClient
    )

    private val scriptContext = ScriptContext.World(this)
    val chunkStorage: ChunkStorage = ChunkStorage(this, server)

    fun loadPersistentState(server: EngineServer) {
        state.copyState(server.loadWorldComponents(this))
    }

    fun tickCallbacks(callbacks: Callbacks) {
        callbacks.of(CallbackType.WORLD_TICK)?.execute(scriptContext)
        if (simulation.ticks % 20 == 0L) {
            callbacks.of(CallbackType.WORLD_TICK_20)?.execute(scriptContext)
        }
    }

    /**
     * Создает сущность с компонентами `event` и Event. Следует использовать как альтернативу очередям событий.
     * Последний сигнализирует о том, что сущность нужно уничтожить в конце тика
     */
    inline fun <reified T : Component> emitEvent(
        event: T,
        type: ComponentType<T>,
        networked: Boolean = false
    ): EntityId {
        return componentManager.addEntity {
            setComponent(event, type)
            setComponent(Event)
            if (networked) {
                setComponent(Networked)
                setComponent(
                    PersistentIdComponent(
                        persistentId(
                            "event-${
                                System.identityHashCode(
                                    event
                                )
                            }"
                        )
                    )
                )
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
