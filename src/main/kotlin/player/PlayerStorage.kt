package org.lain.engine.player

import org.lain.engine.util.Storage
import java.util.concurrent.ConcurrentHashMap

class PlayerStorage(
    private val players: MutableMap<PlayerId, EnginePlayer> = ConcurrentHashMap()
) {
    val all
        get() = players.values

    fun get(playerId: PlayerId): EnginePlayer? = players[playerId]
    fun require(playerId: PlayerId) = get(playerId) ?: throw PlayerNotFoundException(playerId)
    fun add(player: EnginePlayer) {
        players.compute(player.id) { id, existing ->
            if (existing != null) {
                throw PlayerCollisionException(id)
            }
            player
        }
    }
    fun remove(player: EnginePlayer) {
        players.remove(player.id)
    }
}

class PlayerNotFoundException(id: PlayerId) : Exception("Player $id not exists")

class PlayerCollisionException(id: PlayerId) : Exception("Player $id already exists")