package org.lain.engine.world

import org.lain.engine.item.GunShoot
import org.lain.engine.util.Component
import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState
import org.lain.engine.util.require
import org.lain.engine.util.set
import java.util.*
import kotlin.apply

class World(
    val id: WorldId,
    private val state: ComponentState = ComponentState()
) : ComponentManager by state

fun world(id: WorldId): World {
    return World(id).apply {
        set(ScenePlayers())
        set(WorldEvents())
    }
}

data class WorldEvents(
    val sounds: Queue<WorldSoundPlayRequest> = LinkedList(),
    val shoots: Queue<GunShoot> = LinkedList(),
) : Component

val World.events get() = this.require<WorldEvents>()