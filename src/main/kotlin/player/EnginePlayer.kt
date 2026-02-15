package org.lain.engine.player

import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState
import org.lain.engine.util.Entity
import org.lain.engine.world.pos

class EnginePlayer(
    val id: PlayerId,
    val state: ComponentState = ComponentState()
) : Entity, ComponentManager by state {
    override val stringId: String get() = id.toString()

    override fun toString(): String {
        return "EnginePlayer($username ($displayName), $id, $pos)"
    }
}