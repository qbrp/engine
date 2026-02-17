package org.lain.engine.world

import org.lain.engine.util.Component
import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState
import org.lain.engine.util.require
import org.lain.engine.util.set
import java.util.*
import kotlin.apply
import kotlin.reflect.KClass

class World(
    val id: WorldId,
    val chunkStorage: ChunkStorage = ChunkStorage(),
    private val state: ComponentState = ComponentState()
) : ComponentManager by state

fun world(id: WorldId): World {
    return World(id).apply {
        set(ScenePlayers())
        set(WorldEvents())
    }
}

data class WorldEvents(
    private val queues: MutableMap<KClass<*>, Queue<*>> = mutableMapOf()
) : Component {

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getQueue(eventClass: KClass<T>): Queue<T> {
        return queues.getOrPut(eventClass) { LinkedList<T>() } as Queue<T>
    }
}

inline fun <reified T : Any> World.events(): Queue<T> = this.require<WorldEvents>().getQueue(T::class)

val World.events get() = this.require<WorldEvents>()

