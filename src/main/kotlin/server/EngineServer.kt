package org.lain.engine.server

import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.handle
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.require
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
import org.lain.engine.script.flushEntityRpcMessageReceiver
import org.lain.engine.script.handleEntityDebugView
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.adaptScriptLightComponents
import org.lain.engine.script.lua.adaptScriptNetworkingComponents
import org.lain.engine.script.lua.adaptScriptPlayerComponents
import org.lain.engine.script.scriptContext
import org.lain.engine.storage.ChunkLoader
import org.lain.engine.storage.CustomPersistentId
import org.lain.engine.storage.ItemLoader
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.SaveTimers
import org.lain.engine.storage.playerData
import org.lain.engine.storage.savePersistentPlayerData
import org.lain.engine.storage.updateUnloadSystem
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.Log
import org.lain.engine.util.Timestamp
import org.lain.engine.util.flush
import org.lain.engine.util.forEachWithContext
import org.lain.engine.util.forEachWithSelfContext
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
    val isReplay: Boolean,
    savePath: File,
    database: Database,
    val luaContext: LuaContext,
    val saveTimers: SaveTimers
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

    @Volatile
    var tick: ULong = 0L.toULong()

    val defaultWorld
        get() = worlds.toList().first().second

    fun listWorlds() = worlds.values

    fun assertOnThread(): Boolean {
        return Thread.currentThread() === thread
    }

    fun run() {
        handler.run()
    }

    fun stop() {
        stopped = true
        handler.invalidate()
    }

    fun update(
        prepareData: World.() -> Unit,
        updateBulletHitSystem: World.() -> Unit,
        updateSaveSystem: World.() -> Unit,
    ) = with(namespacedStorage) {
        if (stopped) return
        val start = Timestamp()
        val vocalSettings = globals.vocalSettings
        val worlds = allWorlds()

        // Подготовка данных
        tick++
        listWorlds().forEach { world ->
            itemLoader.applyCommands(world)
            playerLoader.applyCommands(world)
        } // принимаем команды из ECS-очередей
        taskQueue.flush { it.run() }

        worlds.forEachWithSelfContext { world ->
            world.prepareData()

            // Фаза 2.1. Обновление игрока
            world.players.forEach { player ->
                handleEntityDebugView(handler, player) // Отсылаем слепок данных игроку

                // Движение, голос
                updatePlayerMovement(player, globals.defaultPlayerAttributes.movement, globals.movementSettings)
                updatePlayerSpeaking(player, chat, vocalSettings)
                updatePlayerVoice(player, chat, globals.vocalSettings)

                // Сбор взаимодействий
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

                updateHearing(player)
                updateAcousticHearing(player, handler, globals.chatSettings)

                tickNarrations(player)
            }

            // Обновление оружейных систем
            updateFireTimeSystem()
            updateRecoilSystem()
            updateBulletsAcoustic(world)
            updateBulletHitSystem()

            // Обновление звуков
            val sounds = processWorldSounds(namespacedStorage, world)
            broadcastWorldSounds(sounds, handler)

            // Вызов обновления системы контейнеров
            updateContainerSystems()

            with(luaContext) {
                adaptScriptNetworkingComponents()
                world.tickCallbacks(callbacks)
                flushEntityRpcMessageReceiver()
                adaptScriptPlayerComponents()
                adaptScriptLightComponents()
            }

            // Обработка взаимодействий с вокселями
            world.updateVoxelEvents(handler)

            // Подгон данных контейнеров
            postUpdateContainerSystems()

            updateSaveSystem()
            updateUnloadSystem(handler, world, saveTimers)

            saveTimers.items.tick()
            saveTimers.containers.tick()

            world.clearEvents()
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
        player.entityId.setComponent(PersistentIdComponent(CustomPersistentId(player.id.toString())))
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

    fun logInMainThread(loggerGetter: EngineServer.(tick: ULong) -> Log) {
        val tick = tick
        execute {
            EngineLogger.log(loggerGetter(tick))
        }
    }

    fun logInMainThread(world: World, loggerGetter: context(World) EngineServer.(tick: ULong) -> Log) {
        val tick = tick
        execute {
            with(world) {
                EngineLogger.log(loggerGetter(tick))
            }
        }
    }
}