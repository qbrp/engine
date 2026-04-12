package org.lain.engine.world

import org.lain.cyberia.ecs.*
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.Callbacks
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptContext
import org.lain.engine.util.component.ComponentWorld

object Event : Component

class World(
    val id: WorldId,
    val componentManager: ComponentWorld,
    val players: MutableList<EnginePlayer> = mutableListOf(),
    val playersWatchingChunkProvider: EnginePlayersWatchingChunkProvider? = null,
    val isClient: Boolean = false,
) : MutableComponentAccess by componentManager, IterationComponentAccess by componentManager {
    private val scriptContext = ScriptContext.World(this)
    val chunkStorage: ChunkStorage = ChunkStorage()
    var ticks = 0L


    fun tickCallbacks(callbacks: Callbacks) {
        callbacks.worldTick.execute(scriptContext)
        if (ticks % 20 == 0L) {
            callbacks.worldTickSecond.execute(scriptContext)
        }
        ticks++
    }

    fun registerScriptComponents(components: List<ScriptComponentType>) {
        componentManager.invalidateComponentArrays(components.map { it.ecsType to it.meta })
    }

    /**
     * Создает сущность с компонентами `event` и Event. Следует использовать как альтернативу очередям событий.
     * Последний сигнализирует о том, что сущность нужно уничтожить в конце тика
     */
    inline fun <reified T : Component> emitEvent(event: T, type: ComponentType<T>) {
        componentManager.addEntity {
            setComponent(event, type)
            setComponent(Event)
        }
    }

    inline fun <reified T : Component> emitEvent(event: T) {
        emitEvent(event, componentTypeOf(T::class))
    }

    fun clearEvents() {
        componentManager.iterate<Event> { entity, _ -> entity.destroy() }
    }
}

fun world(id: WorldId, thread: Thread, playersWatchingChunkProvider: EnginePlayersWatchingChunkProvider? = null): World {
    return World(id, playersWatchingChunkProvider=playersWatchingChunkProvider, componentManager = ComponentWorld(thread))
}

