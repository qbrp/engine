package org.lain.engine.server

import kotlinx.coroutines.Dispatchers
import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.player.PlayerService
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.flushPlayerMessages
import org.lain.engine.player.flushPlayerUpdates
import org.lain.engine.player.updatePlayerMovement
import org.lain.engine.player.updatePlayerVoice
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.Timestamp
import org.lain.engine.util.flush
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import java.util.concurrent.ConcurrentLinkedQueue

class EngineServer(
    id: ServerId,
    val playerStorage: PlayerStorage,
    val acousticSimulator: AcousticSimulator,
    val eventListener: ServerEventListener,
    val transportContext: ServerTransportContext
) {
    val globals: ServerGlobals = ServerGlobals(id)
    val handler = ServerHandler(playerStorage, this, transportContext)

    private val taskQueue = ConcurrentLinkedQueue<Runnable>()
    private val worlds: MutableMap<WorldId, World> = mutableMapOf()

    val tickTimes = FixedSizeList<Int>(20)
    val chat: EngineChat = EngineChat(acousticSimulator, this)
    val playerService = PlayerService(playerStorage, this)
    val defaultWorld
        get() = worlds.toList().first().second

    fun run() {
        handler.run()
        update()
    }

    fun stop() {
        handler.invalidate()
    }

    fun update() {
        val start = Timestamp()
        val players = playerStorage.getAll()
        val vocalSettings = globals.vocalSettings
        for (player in players) {
            updatePlayerMovement(player, globals.defaultPlayerAttributes.movement, globals.movementSettings)
            flushPlayerMessages(player, chat, vocalSettings)
            updatePlayerVoice(player, chat, globals.vocalSettings)
        }

        players.forEach { flushPlayerUpdates(it, handler) }

        handler.synchronizePlayers()
        taskQueue.flush { it.run() }

        tickTimes.add(start.timeElapsed().toInt())
    }

    fun updateGlobals(update: (ServerGlobals) -> Unit) = execute {
        update(globals)
        chat.onSettingsUpdated(globals.chatSettings)
        handler.onServerSettingsUpdate()
    }

    fun execute(r: Runnable) {
        taskQueue += r
    }

    fun addWorld(world: World) {
        worlds[world.id] = world
    }

    fun getWorld(id: WorldId): World {
        return worlds[id] ?: throw IllegalArgumentException("World with id $id not found")
    }

    fun allWorlds() = worlds.values
}