package org.lain.engine.player

import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState
import org.lain.engine.world.pos

class Player(
    val id: PlayerId,
    val state: ComponentState = ComponentState()
) : ComponentManager by state {
    override fun toString(): String {
        return "Player($username ($displayName), $id, $pos)"
    }
}