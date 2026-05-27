package org.lain.engine.server

import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.handle
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
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
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.scriptContext
import org.lain.engine.storage.ChunkLoader
import org.lain.engine.storage.ItemLoader
import org.lain.engine.storage.playerData
import org.lain.engine.storage.savePersistentPlayerData
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.Timestamp
import org.lain.engine.util.flush
import org.lain.engine.util.forEachWithContext
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
    val namespacedStorage: NamespacedStorageAccess,
    val thread: Thread,
    savePath: File,
    database: Database
): Executor {
    @Volatile
    var globals: ServerGlobals = ServerGlobals(id, savePath=savePath)
        private set
    val handler = ServerHandler(this)

    private val taskQueue = ConcurrentLinkedQueue<Runnable>()
    internal val worlds: MutableMap<WorldId, World> = mutableMapOf()

    var stopped = false
    val tickTimes = FixedSizeList<Int>(20)
    val chat: EngineChat = EngineChat(acousticSimulator, this)
    val voidContainer by lazy { defaultWorld.createContainer(Location(Vec3(0f))) }
    var callbacks = Callbacks()
    val itemLoader = ItemLoader(this, database)
    val playerLoader = PlayerLoader(this, itemLoader)
    val chunkLoader = ChunkLoader(this, database)

    val defaultWorld
        get() = worlds.toList().first().second

    fun listWorlds() = worlds.values

    fun assertOnThread(): Boolean {
        return Thread.currentThread() === thread
    }

    fun run() {
        handler.run()
        update()
    }

    fun stop() {
        stopped = true
        handler.invalidate()
    }

    fun preUpdate() {
        listWorlds().forEach { world ->
            itemLoader.applyCommands(world)
            playerLoader.applyCommands(world)
        }
    }

    fun update() = with(namespacedStorage) {
        if (stopped) return
        val start = Timestamp()
        val vocalSettings = globals.vocalSettings
        val worlds = allWorlds()

        taskQueue.flush { it.run() }

        worlds.forEachWithContext({ it }) { world ->
            world.players.forEach { player ->
                updatePlayerMovement(player, globals.defaultPlayerAttributes.movement, globals.movementSettings)
                updatePlayerSpeaking(player, chat, vocalSettings)
                updatePlayerVoice(player, chat, globals.vocalSettings)

                updatePlayerVerbLookup(player)
                appendVerbs(player)
                updatePlayerInteractions(player, handler=handler)

                player.handle<InteractionComponent>() {
                    handlePlayerInventoryInteractions(player)
                    handleWriteableInteractions(player)
                    handleGunInteractions(player)
                    handleSocialInteractions(player)
//                    handleFlashlightInteractions(player)
//                    handlePlayerEquipmentInteractionProgression(player)
//                    handlePlayerEquipmentInteraction(player)
                    handleHandScriptInteractions(player)
                    finishPlayerInteraction(player)
                }

                updateFireTimeSystem()
                updateRecoilSystem()
                updateHearing(player)
                updateAcousticHearing(player, handler, globals.chatSettings)

                tickNarrations(player)
            }

            val sounds = processWorldSounds(namespacedStorage, world)
            broadcastWorldSounds(sounds, handler)
            updateBulletsAcoustic(world)
            updateContainerSystems()
            world.tickCallbacks(callbacks)
        }

        handler.tick()
        tickTimes.add(start.timeElapsed().toInt())
    }

    fun postUpdate() {
        listWorlds().forEachWithContext({ it }) { world ->
            postUpdateContainerSystems()
        }
    }

    fun updateGlobals(update: (ServerGlobals) -> ServerGlobals) = execute {
        globals = update(globals)
        chat.onSettingsUpdated(globals.chatSettings)
        handler.onServerSettingsUpdate()
        //playerStorage.forEach { player -> player.replace(globals.defaultPlayerAttributes) }
    }

    fun instantiatePlayer(player: EnginePlayer, notifications: List<Notification> = listOf()) = with(player.world) {
        player.entityId.setComponent(Player(player))
        player.entityId.setComponent(player.location)
        eventListener.onPlayerInstantiated(player)

        if (globals.spectateOnJoin) player.startSpectating()
        playerStorage.add(player.id, player)
        players += player
        handler.onPlayerInstantiation(player, notifications)

        chat.trySendJoinMessage(player)
        callbacks.playerInstantiate.execute(player.scriptContext)
    }

    fun destroyPlayer(player: EnginePlayer) = with(player.world) {
        playerStorage.remove(player.id)
        players -= player

        (player.collectOwnedItems(this) + player.items).forEach { item -> item.removeComponent<HoldsBy>() }

        player.equipmentContainer.destroy()
        player.mainContainer.destroy()

        chat.trySendLeaveMessage(player)
        handler.onPlayerDestroy(player)
        callbacks.playerDestroy.execute(player.scriptContext)
        globals.savePath.playerData.savePersistentPlayerData(player)
        player.entityId.destroy()
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