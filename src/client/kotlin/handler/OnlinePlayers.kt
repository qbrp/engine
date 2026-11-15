package org.lain.engine.client.handler

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.transport.packet.GeneralPlayerData

data class OnlinePlayer(
    val id: PlayerId,
    val username: Username
)

class OnlinePlayers {
    val playersById = mutableMapOf<PlayerId, OnlinePlayer>()

    fun get(id: PlayerId) = playersById[id]

    fun add(player: GeneralPlayerData): OnlinePlayer {
        val onlinePlayer = OnlinePlayer(player.id, player.username)
        playersById[player.id] = onlinePlayer
        return onlinePlayer
    }

    fun remove(id: PlayerId) = playersById.remove(id)
}