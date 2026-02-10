package org.lain.engine.player

import org.lain.engine.chat.trySendJoinMessage
import org.lain.engine.chat.trySendLeaveMessage
import org.lain.engine.item.HoldsBy
import org.lain.engine.server.EngineServer
import org.lain.engine.storage.savePersistentPlayerData
import org.lain.engine.util.Storage
import org.lain.engine.util.remove
import org.lain.engine.util.require
import org.lain.engine.world.ScenePlayers
import org.lain.engine.world.world

typealias PlayerStorage = Storage<PlayerId, EnginePlayer>

//TODO: Убрать, переделать в чистые функции и отдать на попечение EngineServer
class PlayerService(
    private val playerStorage: PlayerStorage,
    server: EngineServer
) {
    private val handler = server.handler
    private val eventListener = server.eventListener
    private val chat = server.chat

    fun instantiate(player: EnginePlayer) {
        val world = player.world
        eventListener.onPlayerInstantiated(player)

        player.startSpectating()
        playerStorage.add(player.id, player)
        world.require<ScenePlayers>().add(player)
        handler.onPlayerInstantiation(player)

        chat.trySendJoinMessage(player)
    }

    fun destroy(player: EnginePlayer) {
        playerStorage.remove(player.id)
        player.world.require<ScenePlayers>().remove(player)

        player.items.forEach { item -> item.remove<HoldsBy>() }

        chat.trySendLeaveMessage(player)
        handler.onPlayerDestroy(player)
        savePersistentPlayerData(player)
        player.removeAll()
    }
}