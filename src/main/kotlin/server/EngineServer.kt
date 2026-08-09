package org.lain.engine.server

import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.chat.trySendJoinMessage
import org.lain.engine.chat.trySendLeaveMessage
import org.lain.engine.container.createContainer
import org.lain.engine.container.postUpdateContainerSystems
import org.lain.engine.container.updateContainerSystems
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.applyCharacter
import org.lain.engine.player.character.removeCharacter
import org.lain.engine.player.interaction.tickSocialActionSystem
import org.lain.engine.player.interaction.tickGunActionSystem
import org.lain.engine.player.interaction.tickPlayerInput
import org.lain.engine.player.interaction.tickWritableActionSystem
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptSystemDispatcher
import org.lain.engine.script.flushEntityRpcMessageReceiver
import org.lain.engine.script.handleEntityDebugView
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.ecs.applyLugLightComponents
import org.lain.engine.script.lua.library.ecs.applyLuaNetworkingComponents
import org.lain.engine.script.lua.library.ecs.applyLuaPlayerComponents
import org.lain.engine.script.lua.library.ecs.prepareLuaScriptComponents
import org.lain.engine.script.lua.library.ecs.refreshLuaComponentsView
import org.lain.engine.script.lua.library.tickScriptVoxelAdapter
import org.lain.engine.script.scriptContext
import org.lain.engine.storage.ChunkLoader
import org.lain.engine.storage.ItemLoader
import org.lain.engine.storage.PersistentCharacterData
import org.lain.engine.storage.SaveTimers
import org.lain.engine.storage.playerData
import org.lain.engine.storage.savePersistentPlayerData
import org.lain.engine.storage.updateUnloadSystem
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.FixedSizeList
import org.lain.engine.util.Log
import org.lain.engine.util.Timestamp
import org.lain.engine.util.flush
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
    val platform: ServerPlatform,
    val namespacedStorage: NamespacedStorageAccess,
    val thread: Thread,
    val isReplay: Boolean,
    savePath: File,
    database: Database,
    val luaScriptEngine: LuaScriptEngine,
    val saveTimers: SaveTimers
): Executor {
    @Volatile
    var globals: ServerGlobals = ServerGlobals(id, savePath=savePath)
        private set
    val handler = ServerHandler(this)
    val dispatcher = asCoroutineDispatcher()

    private val taskQueue = ConcurrentLinkedQueue<Runnable>()
    internal val worlds: MutableMap<WorldId, World> = mutableMapOf()

    var stopped = false
    val tickTimes = FixedSizeList<Int>(20)
    val chat: EngineChat = EngineChat(acousticSimulator, this)
    val voidContainer by lazy { defaultWorld.createContainer(Location(Vec3(0f))) }
    var callbacks = Callbacks()
    val scriptSystemDispatcher = ScriptSystemDispatcher()
    val itemLoader = ItemLoader(this, database)
    val playerLoader = PlayerLoader(this, itemLoader)
    val chunkLoader = ChunkLoader(this, database)

    @Volatile
    var tick: ULong = ULong.MIN_VALUE

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
        taskQueue.flush { it.run() }

        worlds.forEachWithSelfContext { world ->
            world.prepareData()
            world.resetItemOwnershipState()
            world.tickItemOwnershipSystem()

            // Фаза 2.1. Обновление игрока
            world.tickPlayerModelSystem()

            world.tickPlayerInput(callbacks)

            world.tickGunActionSystem()
            world.tickSocialActionSystem(playerStorage)
            world.tickWritableActionSystem()

            world.tickMovementSystem(globals.defaultPlayerAttributes.movement, globals.movementSettings)

            world.players.forEach { player ->
                handleEntityDebugView(handler, player) // Отсылаем слепок данных игроку

                // Движение, голос
                updatePlayerSpeaking(player, chat, vocalSettings)
                updatePlayerVoice(player, chat, globals.vocalSettings)

                updateHearing(player)
                updateAcousticHearing(player, handler, globals.chatSettings)

                tickNarrations(player)
            }

            // Обновление оружейных систем
            world.tickMagazineSystem()
            world.tickGunSystem()
            world.tickRecoilSystem()
            updateBulletsAcoustic(world)
            updateBulletHitSystem()

            // Вызов обновления системы контейнеров
            updateContainerSystems()

            with(luaScriptEngine) {
                applyLuaNetworkingComponents()
                world.tickCallbacks(callbacks)
                scriptSystemDispatcher.tick(world)
                flushEntityRpcMessageReceiver()
                refreshLuaComponentsView()
                applyLugLightComponents()
                tickScriptVoxelAdapter()
                applyLuaPlayerComponents()
            }

            // Обработка взаимодействий с вокселями
            world.updateVoxelEvents(handler)

            // Подгон данных контейнеров
            postUpdateContainerSystems()

            world.tickSynchronizationSystem(this@EngineServer)

            updateSaveSystem()
            updateUnloadSystem(handler, world, saveTimers)
        }

        saveTimers.items.tick()
        saveTimers.containers.tick()

        tickTimes.add(start.timeElapsed().toInt())

        worlds.forEach { world -> world.clearEvents() }
    }

    fun updateGlobals(update: (ServerGlobals) -> ServerGlobals) = execute {
        globals = update(globals)
        chat.onSettingsUpdated(globals.chatSettings)
        handler.onServerSettingsUpdate()
        //playerStorage.forEach { player -> player.replace(globals.defaultPlayerAttributes) }
    }

    fun instantiatePlayer(
        player: EnginePlayer,
        notifications: List<Notification> = listOf(),
        engineCharacter: EngineCharacter? = null,
        characterPersistentCharacter: PersistentCharacterData? = null
    ) = with(player.world) {
        playerStorage.add(player.id, player)
        players += player
        platform.onPlayerInstantiated(player)

        with(luaScriptEngine) { player.prepareLuaScriptComponents() }
        engineCharacter?.let { player.applyCharacter(engineCharacter, characterPersistentCharacter, platform) }
        if (!globals.spectateOnJoin) { player.stopSpectating() }
        handler.onPlayerInstantiation(player, notifications)

        chat.trySendJoinMessage(player)
        callbacks.of(CallbackType.PLAYER_INSTANTIATE)?.execute(player.scriptContext)
    }

    fun destroyPlayer(player: EnginePlayer) = with(player.world) {
        playerStorage.remove(player.id)
        player.destroyed = true
        players -= player

        (player.collectOwnedItems(this) + player.items).forEach { item -> item.removeComponent<HeldBy>() }

        player.equipmentContainer.destroy()
        player.mainContainer.destroy()
        player.removeCharacter(platform)

        callbacks.of(CallbackType.PLAYER_DESTROY)?.execute(player.scriptContext)

        chat.trySendLeaveMessage(player)
        handler.onPlayerDestroy(player)
        globals.savePath.playerData.savePersistentPlayerData(player)
        player.entity.destroy()
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
