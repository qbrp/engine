package org.lain.engine.server

import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.Timestamp
import org.lain.engine.util.flush
import org.lain.engine.world.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class EngineServer(
    id: ServerId,
    val playerStorage: PlayerStorage,
    val acousticSimulator: AcousticSimulator,
    val eventListener: ServerEventListener,
    val thread: Thread,
    savePath: File,
) {
    val globals: ServerGlobals = ServerGlobals(id, savePath=savePath)
    val handler = ServerHandler(this)

    private val taskQueue = ConcurrentLinkedQueue<Runnable>()
    private val worlds: MutableMap<WorldId, World> = mutableMapOf()

    val tickTimes = FixedSizeList<Int>(20)
    val chat: EngineChat = EngineChat(acousticSimulator, this)
    val playerService = PlayerService(playerStorage, this)
    val itemStorage = ItemStorage()
    val namespacedStorage = NamespacedStorage()
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
            updatePlayerInteractions(player, handler=handler)
            val playerItems = player.items
            updateGunState(playerItems)
            handleGunShotTags(player, playerItems)
            supplyPlayerInventoryItemsLocation(player, playerItems)
        }

        players.forEach { flushPlayerUpdates(it, handler) }

        handler.tick()
        taskQueue.flush { it.run() }

        tickTimes.add(start.timeElapsed().toInt())
        worlds.values.forEach { world ->
            processWorldSounds(handler, namespacedStorage, globals.defaultItemSounds, world)
            handleDecalsAttaches(world)
            broadcastDecalsAttachments(handler, world)
            world.events<DecalEvent>().clear()
        }
    }

    fun updateGlobals(update: (ServerGlobals) -> Unit) = execute {
        update(globals)
        chat.onSettingsUpdated(globals.chatSettings)
        handler.onServerSettingsUpdate()
    }

    fun execute(r: Runnable) {
        if (isOnThread()) {
            r.run()
        } else {
            taskQueue += r
        }
    }

    fun isOnThread() = Thread.currentThread() == thread

    fun addWorld(world: World) {
        worlds[world.id] = world
    }

    fun getWorld(id: WorldId): World {
        return worlds[id] ?: throw IllegalArgumentException("World with id $id not found")
    }

    fun createItem(location: Location, id: ItemId): EngineItem {
        val prefab = namespacedStorage.items[id] ?: error("Item with id $id not found")
        val item = bakeItem(location, prefab)
        itemStorage.add(item.uuid, item)
        return item
    }

    fun allWorlds() = worlds.values
}