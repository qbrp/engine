package org.lain.engine.player

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.chat.trySendLeaveMessage
import org.lain.engine.data.snapshotPersistent
import org.lain.engine.player.character.unloadCharacter
import org.lain.engine.server.EngineServer
import org.lain.engine.world.World

data class PlayerDisconnectEvent(
    val player: EnginePlayer
) : Component

fun World.cleanupDisconnectedPlayers(server: EngineServer) {
    iterate<PlayerDisconnectEvent> { _, (player) ->
        try {
            simulation.preparePlayerDestroy(player)
            player.unloadCharacter(server.platform, server.playerPersistence)
            server.chat.trySendLeaveMessage(player)
            server.handler.onPlayerDestroy(player)
            server.playerPersistence.save(player.snapshotPersistent())
        } finally {
            server.characterManager.release(player)
            simulation.destroyPlayer(player)
        }
    }
}
