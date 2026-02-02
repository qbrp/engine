package org.lain.engine.world

import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.Component
import org.lain.engine.util.require

data class ScenePlayers(
    val players: MutableList<EnginePlayer> = mutableListOf()
) : Component {
    fun add(player: EnginePlayer) {
        players += player
    }

    fun remove(player: EnginePlayer) {
        players -= player
    }
}

val World.players
    get() = this.require<ScenePlayers>().players