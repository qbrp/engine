package org.lain.engine.server

import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.chat.trySendJoinMessage
import org.lain.engine.chat.trySendLeaveMessage
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.storage.savePersistentPlayerData
import org.lain.engine.util.*
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

    var stopped = false
    val tickTimes = FixedSizeList<Int>(20)
    val chat: EngineChat = EngineChat(acousticSimulator, this)
    val itemStorage = ItemStorage()
    val namespacedStorage = NamespacedStorage()
    val defaultWorld
        get() = worlds.toList().first().second

    fun run() {
        handler.run()
        update()
    }

    fun stop() {
        stopped = true
        handler.invalidate()
    }

    fun update() {
        if (stopped) return
        val start = Timestamp()
        val players = playerStorage.getAll()
        val vocalSettings = globals.vocalSettings

        taskQueue.flush { it.run() }

        for (player in players) {
            val playerItems = player.items
            updatePlayerMovement(player, globals.defaultPlayerAttributes.movement, globals.movementSettings)
            supplyPlayerInventoryItemsLocation(player, playerItems)
            flushPlayerMessages(player, chat, vocalSettings)
            updatePlayerVoice(player, chat, globals.vocalSettings)

            updatePlayerVerbLookup(player)
            appendVerbs(player)

            updatePlayerInteractions(player, handler=handler)
            handlePlayerInventoryInteractions(player)
            handleWriteableInteractions(player)
            handleGunInteractions(player)
            handleSocialInteractions(player)
            finishPlayerInteraction(player)
            tickInventoryGun(playerItems)

            handleItemRecoil(player, playerItems)
            player.input.clear()

            tickNarrations(player)
        }

        handler.tick()

        worlds.values.forEach { world ->
            handleDecalsAttaches(world)
            broadcastDecalsAttachments(handler, world)
            world.events<DecalEvent>().clear()
            val sounds = processWorldSounds(namespacedStorage, world)
            broadcastWorldSounds(sounds, handler)
        }
        tickTimes.add(start.timeElapsed().toInt())
    }

    fun updateGlobals(update: (ServerGlobals) -> Unit) = execute {
        update(globals)
        chat.onSettingsUpdated(globals.chatSettings)
        handler.onServerSettingsUpdate()
    }

    fun instantiatePlayer(player: EnginePlayer) {
        val world = player.world
        eventListener.onPlayerInstantiated(player)

        player.startSpectating()
        playerStorage.add(player.id, player)
        world.require<ScenePlayers>().add(player)
        handler.onPlayerInstantiation(player)

        chat.trySendJoinMessage(player)
    }

    fun destroyPlayer(player: EnginePlayer) {
        playerStorage.remove(player.id)
        player.world.require<ScenePlayers>().remove(player)

        player.items.forEach { item -> item.remove<HoldsBy>() }

        chat.trySendLeaveMessage(player)
        handler.onPlayerDestroy(player)
        savePersistentPlayerData(player)
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