package org.lain.engine.world

import org.lain.engine.player.Player
import org.lain.engine.util.Component
import org.lain.engine.util.require

data class ScenePlayers(
    val players: MutableList<Player> = mutableListOf()
) : Component {
    fun add(player: Player) {
        players += player
    }

    fun remove(player: Player) {
        players -= player
    }
}

val World.players
    get() = this.require<ScenePlayers>().players