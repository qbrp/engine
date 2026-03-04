package org.lain.engine.world

import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.component.*

object Event : Component

class World(
    val id: WorldId,
    val chunkStorage: ChunkStorage = ChunkStorage(),
    val players: MutableList<EnginePlayer> = mutableListOf(),
    val componentManager: ComponentWorld = ComponentWorld()
) : ComponentAccess by componentManager {
    init {
        componentManager.registerComponents()
    }

    /**
     * Создает сущность с компонентами `event` и Event. Следует использовать как альтернативу очередям событий.
     * Последний сигнализирует о том, что сущность нужно уничтожить в конце тика
     */
    fun emitEvent(event: Component) {
        val entity = componentManager.addEntity()
        componentManager.setComponent(entity,event)
        componentManager.setComponent(entity, Event)
    }

    fun clearEvents() {
        componentManager.iterate<Event> { entity, _ -> entity.destroy() }
    }
}

fun world(id: WorldId): World {
    return World(id)
}

