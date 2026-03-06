package org.lain.engine.player

import org.lain.engine.util.component.ComponentManager
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.Entity
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