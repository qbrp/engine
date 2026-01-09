package org.lain.engine.server

import org.lain.engine.player.Player
import org.lain.engine.util.Component
import org.lain.engine.util.require

data class SynchronizationComponent(
    var authorized: Boolean,
    val synchronizedPlayers: MutableList<Player> = mutableListOf()
) : Component

val Player.synchronization
    get() = this.require<SynchronizationComponent>()