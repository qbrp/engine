package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.engine.item.WorldGunEvents
import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState
import org.lain.engine.util.set

class World(
    val id: WorldId,
    private val state: ComponentState = ComponentState()
) : ComponentManager by state

fun world(id: WorldId): World {
    return World(id).apply {
        set(ScenePlayers())
        set(WorldSoundsComponent())
        set(WorldGunEvents())
    }
}