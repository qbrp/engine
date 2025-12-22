package org.lain.engine.player

import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState

class Player(
    val id: PlayerId,
    val state: ComponentState = ComponentState()
) : ComponentManager by state