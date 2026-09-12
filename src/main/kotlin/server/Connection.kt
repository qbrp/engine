package org.lain.engine.server

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos

sealed interface ConnectionState {
    val playerId: PlayerId
    fun sendChunk(chunk: EngineChunk, pos: EngineChunkPos)

    class Authorization(override val playerId: PlayerId) : ConnectionState {
        val queuedChunks = mutableListOf<EngineChunkPos>()

        override fun sendChunk(
            chunk: EngineChunk,
            pos: EngineChunkPos
        ) {
            queuedChunks += pos
        }
    }

    class Play(
        val player: EnginePlayer,
        val handler: ServerHandler,
        queuedChunks: List<EngineChunkPos>
    ) : ConnectionState {
        override val playerId: PlayerId
            get() = player.id

        init {
            queuedChunks.forEach {
                sendChunk(player.world.chunkStorage.requireChunk(it), it)
            }
        }

        override fun sendChunk(chunk: EngineChunk, pos: EngineChunkPos) {
            handler.sendChunk(player, chunk, pos)
        }
    }
}

class Connection(
    val playerId: PlayerId,
    var state: ConnectionState
) {
    fun onAuthorized(handler: ServerHandler, player: EnginePlayer) = state.let { state ->
        if (state !is ConnectionState.Authorization) {
            error("Player already authorized")
        }
        val queuedChunks = state.queuedChunks
        this.state = ConnectionState.Play(player, handler, queuedChunks)
    }
}
