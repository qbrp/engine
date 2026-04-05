package org.lain.engine.server

import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.chat.trySendJoinMessage
import org.lain.engine.chat.trySendLeaveMessage
import org.lain.engine.container.createContainer
import org.lain.engine.container.postUpdateContainerSystems
import org.lain.engine.container.updateContainerSystems
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.script.Callbacks
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.scriptContext
import org.lain.engine.storage.ItemLoader
import org.lain.engine.storage.playerData
import org.lain.engine.storage.savePersistentPlayerData
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.Timestamp
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.handle
import org.lain.cyberia.ecs.remove
import org.lain.engine.util.flush
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

class EngineServer(
    id: ServerId,
    val playerStorage: PlayerStorage,
    val acousticSimulator: AcousticSimulator,
    val eventListener: ServerEventListener,
    val thread: Thread,
    savePath: File,
    database: Database
): Executor {
    val globals: ServerGlobals = ServerGlobals(id, savePath=savePath)
    val handler = ServerHandler(this)

    private val taskQueue = ConcurrentLinkedQueue<Runnable>()
    private val worlds: MutableMap<WorldId, World> = mutableMapOf()

    var stopped = false
    val tickTimes = FixedSizeList<Int>(20)
    val chat: EngineChat = EngineChat(acousticSimulator, this)
    val itemStorage = ItemStorage()
    val namespacedStorage = NamespacedStorage()
    val voidContainer by lazy { defaultWorld.createContainer(Location(defaultWorld, Vec3(0f))) }
    var callbacks = Callbacks()
    val itemLoader = ItemLoader(this, database)
    val playerLoader = PlayerLoader(this, itemLoader)

    val defaultWorld
        get() = worlds.toList().first().second

    fun listWorlds() = worlds.values

    fun run() {
        handler.run()
        update()
    }

    fun stop() {
        stopped = true
        handler.invalidate()
    }

    fun update() = with(namespacedStorage) {
        if (stopped) return
        val start = Timestamp()
        val players = playerStorage.getAll()
        val vocalSettings = globals.vocalSettings

        allWorlds().forEach { world ->
            itemLoader.applyCommands(world)
            playerLoader.applyCommands(world)
        }

        taskQueue.flush { it.run() }

        for (player in players) {
            val playerItems = player.items
            updatePlayerMovement(player, globals.defaultPlayerAttributes.movement, globals.movementSettings)
            updatePlayerSpeaking(player, chat, vocalSettings)
            updatePlayerVoice(player, chat, globals.vocalSettings)

            updatePlayerVerbLookup(player)
            appendVerbs(player)
            updatePlayerInteractions(player, handler=handler)

//            val interaction = player.get<InteractionComponent>()
//            if (interaction != null) {
//                println("Взаимодействие: $interaction")
//            }

            player.handle<InteractionComponent>() {
                handlePlayerInventoryInteractions(player)
                handleWriteableInteractions(player)
                handleGunInteractions(player)
                handleSocialInteractions(player)
                handleFlashlightInteractions(player)
                handlePlayerEquipmentInteractionProgression(player)
                handlePlayerEquipmentInteraction(player)
                handleHandScriptInteractions(player)
                finishPlayerInteraction(player)
            }

            tickInventoryGun(playerItems)
            handleItemRecoil(player, playerItems)
            updateHearing(player)
            updateAcousticHearing(player, handler, globals.chatSettings)

            tickNarrations(player)
        }

        listWorlds().forEach { world ->
            val sounds = processWorldSounds(namespacedStorage, world)
            broadcastWorldSounds(sounds, handler)
            updateBulletsAcoustic(world)
            updateContainerSystems(world, itemStorage)
            val scriptContext = ScriptContext.World(world)
            callbacks.worldTick.execute(scriptContext)
            if (world.ticks % 20 == 0L) {
                callbacks.worldTickSecond.execute(scriptContext)
            }
            world.ticks++
        }
        handler.tick()
        tickTimes.add(start.timeElapsed().toInt())
    }

    fun postUpdate() {
        listWorlds().forEach { world -> postUpdateContainerSystems(world) }
    }

    fun updateGlobals(update: (ServerGlobals) -> Unit) = execute {
        update(globals)
        chat.onSettingsUpdated(globals.chatSettings)
        handler.onServerSettingsUpdate()
    }

    fun instantiatePlayer(player: EnginePlayer, notifications: List<Notification> = listOf()) {
        val world = player.world
        eventListener.onPlayerInstantiated(player)

        player.startSpectating()
        playerStorage.add(player.id, player)
        world.players += player
        handler.onPlayerInstantiation(player, notifications)

        chat.trySendJoinMessage(player)
        callbacks.playerInstantiate.execute(player.scriptContext)
    }

    fun destroyPlayer(player: EnginePlayer) = with(player.world) {
        playerStorage.remove(player.id)
        players -= player

        (player.collectOwnedItems(this) + player.items).forEach { item -> item.remove<HoldsBy>() }

        player.equipmentContainer.destroy()
        player.mainContainer.destroy()

        chat.trySendLeaveMessage(player)
        handler.onPlayerDestroy(player)
        callbacks.playerDestroy.execute(player.scriptContext)
        globals.savePath.playerData.savePersistentPlayerData(player)
    }

    override fun execute(r: Runnable) {
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

    fun allWorlds() = worlds.values
}