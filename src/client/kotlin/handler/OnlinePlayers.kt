package org.lain.engine.client.handler

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.transport.packet.GeneralPlayerData

data class OnlinePlayer(
    val id: PlayerId,
    val username: Username
)

class OnlinePlayers(players: List<GeneralPlayerData>, mainPlayer: EnginePlayer) {
    val playersById = mutableMapOf<PlayerId, OnlinePlayer>()

    init {
        players.forEach { add(it) }
        add(GeneralPlayerData.of(mainPlayer))
    }

    fun get(id: PlayerId) = playersById[id]

    fun add(player: GeneralPlayerData) {
        playersById[player.id] = OnlinePlayer(player.id, player.username)
    }

    fun remove(id: PlayerId) = playersById.remove(id)
}