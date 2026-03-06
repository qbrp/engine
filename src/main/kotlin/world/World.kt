package org.lain.engine.world

import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.component.*
import kotlin.reflect.KClass

object Event : Component

class World(
    val id: WorldId,
    val players: MutableList<EnginePlayer> = mutableListOf(),
    val componentManager: ComponentWorld = ComponentWorld(),
    val playersWatchingChunkProvider: EnginePlayersWatchingChunkProvider? = null,
) : ComponentAccess by componentManager {
    val chunkStorage: ChunkStorage = ChunkStorage(this)

    init {
        componentManager.registerComponents()
    }

    /**
     * Создает сущность с компонентами `event` и Event. Следует использовать как альтернативу очередям событий.
     * Последний сигнализирует о том, что сущность нужно уничтожить в конце тика
     */
    fun <T : Component> emitEvent(event: T, kclass: KClass<T>) {
        val entity = componentManager.addEntity()
        componentManager.setComponentWithType(entity,event, kclass)
        componentManager.setComponent(entity, Event)
    }

    inline fun <reified T : Component> emitEvent(event: T) {
        emitEvent(event, T::class)
    }

    fun clearEvents() {
        componentManager.iterate<Event> { entity, _ -> entity.destroy() }
    }
}

fun world(id: WorldId, playersWatchingChunkProvider: EnginePlayersWatchingChunkProvider? = null): World {
    return World(id, playersWatchingChunkProvider=playersWatchingChunkProvider)
}

