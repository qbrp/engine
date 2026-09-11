package org.lain.engine.server

import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.EngineSimulation
import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.chat.trySendJoinMessage
import org.lain.engine.chat.trySendLeaveMessage
import org.lain.engine.player.*
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.removeCharacter
import org.lain.engine.player.interaction.PlayerInputMode
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.tickEntityDebugViewSnapshotSystem
import org.lain.engine.storage.*
import org.lain.engine.util.*
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.updateVoxelEvents
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor

class EngineServer(
    id: ServerId,
    val playerStorage: PlayerStorage,
    val acousticSimulator: AcousticSimulator,
    val platform: ServerPlatform,
    val namespacedStorage: NamespacedStorageAccess,
    val moduleManager: ModuleManager,
    val thread: Thread,
    val isReplay: Boolean,
    savePath: File,
    database: Database,
    val luaScriptEngine: LuaScriptEngine,
    val saveTimers: SaveTimers,
): Executor, EngineSimulation.SimulationTickExtension, EngineSimulation.Settings {
    val handler = ServerHandler(this)
    val dispatcher = asCoroutineDispatcher()

    @Volatile
    var globals: ServerGlobals = ServerGlobals(id, savePath=savePath)
        private set

    private val taskQueue = ConcurrentLinkedQueue<Runnable>()

    var stopped = false
    val tickTimes = FixedSizeList<Int>(20)
    val chat: EngineChat = EngineChat(acousticSimulator, this)
    val itemLoader = ItemLoader(this, database)
    val playerLoader = PlayerLoader(this, itemLoader)
    val chunkLoader = ChunkLoader(this, database)
    val simulation = EngineSimulation(
        false,
        this,
        this,
        playerStorage,
        namespacedStorage,
        luaScriptEngine,
        thread,
        PlayerInputMode.Authoritative
    )

    fun run() {
        handler.run()
    }

    fun stop() {
        stopped = true
        handler.invalidate()
    }

    override fun World.beforeInput() = with(platform) {
        prepareData()
    }

    override fun World.afterInteractions() {
        val vocalSettings = globals.vocalSettings
        tickEntityDebugViewSnapshotSystem(handler) // Отсылаем слепок данных игроку
        tickPlayerSpeakSystem(chat, vocalSettings)
        tickPlayerVoiceSystem(vocalSettings)
        //updateHearing(player)
        tickAcousticHearingSystem(handler, globals.chatSettings)
    }

    override fun World.afterOperations() = with(platform) {
        // Обработка взаимодействий с вокселями
        updateBulletHitSystem()
        updateVoxelEvents(handler)
    }

    override fun World.beforeEventCleanup() = with(platform) {
        tickSynchronizationSystem(this@EngineServer)
        updateSaveSystem()
        updateUnloadSystem(handler, saveTimers)
    }

    fun update() = with(namespacedStorage) {
        if (stopped) return
        val start = Timestamp()
        taskQueue.flush { it.run() }
        simulation.tick()

        saveTimers.items.tick()
        saveTimers.containers.tick()

        tickTimes.add(start.timeElapsed().toInt())
    }

    fun updateGlobals(update: (ServerGlobals) -> ServerGlobals) = execute {
        globals = update(globals)
        chat.onSettingsUpdated(globals.chatSettings)
        handler.onServerSettingsUpdate()
        playerStorage.all.forEach { player -> player.set(globals.defaultPlayerAttributes) }
    }

    fun instantiatePlayer(
        player: EnginePlayer,
        notifications: List<Notification> = listOf(),
        engineCharacter: EngineCharacter? = null,
        characterPersistentCharacter: PersistentCharacterData? = null,
    ) = with(player.world) {
        simulation.instantiatePlayer(player, engineCharacter, characterPersistentCharacter, platform)

        if (!globals.spectateOnJoin) {
            player.stopSpectating()
        }

        handler.onPlayerInstantiation(player, notifications)
        chat.trySendJoinMessage(player)
    }

    fun destroyPlayer(player: EnginePlayer) = with(player.world) {
        try {
            simulation.preparePlayerDestroy(player)
            player.removeCharacter(platform)
            chat.trySendLeaveMessage(player)
            handler.onPlayerDestroy(player)
            globals.savePath.playerData.savePersistentPlayerData(player)
        } finally {
            simulation.destroyPlayer(player)
        }
    }

    override fun execute(r: Runnable) {
        if (isOnThread()) {
            r.run()
        } else {
            taskQueue += r
        }
    }

    fun isOnThread() = Thread.currentThread() == thread

    fun getWorld(id: WorldId): World {
        return simulation.worlds[id] ?: throw IllegalArgumentException("World with id $id not found")
    }

    fun allWorlds() = simulation.worlds.values

    fun hasPermission(player: EnginePlayer, permission: String) = platform.hasPermission(player, permission)

    fun logInMainThread(loggerGetter: EngineServer.(tick: Long) -> Log) {
        val tick = simulation.ticks
        execute {
            EngineLogger.log(loggerGetter(tick))
        }
    }

    fun logInMainThread(world: World, loggerGetter: context(World) EngineServer.(tick: Long) -> Log) {
        val tick = simulation.ticks
        execute {
            with(world) {
                EngineLogger.log(loggerGetter(tick))
            }
        }
    }
}
