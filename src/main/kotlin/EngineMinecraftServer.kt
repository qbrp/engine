package org.lain.engine

import kotlinx.coroutines.runBlocking
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.storage.LevelResource
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.require
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.chat.IncomingMessage
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAccess
import org.lain.engine.item.ItemId
import org.lain.engine.item.instantiateItem
import org.lain.engine.mc.*
import org.lain.engine.mc.commands.registerIntentCommands
import org.lain.engine.player.*
import org.lain.engine.script.*
import org.lain.engine.script.lua.*
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerEventListener
import org.lain.engine.storage.*
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.ConcurrentStorage
import org.lain.engine.util.Injector
import org.lain.engine.util.file.CONFIG_LOGGER
import org.lain.engine.util.file.ServerConfig
import org.lain.engine.util.file.applyConfigCatching
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.forEachWithContext
import org.lain.engine.world.*

data class EngineMinecraftServerDependencies(
    val minecraftServer: MinecraftServer,
    val luaContext: LuaContext,
    val compilationResult: CompilationResult,
    val config: ServerConfig = loadOrCreateServerConfig(),
    val namespacedStorage: NamespacedStorageAccess,
    val playerStorage: PlayerStorage = ConcurrentStorage(),
    val entityTable: EntityTable = Injector.resolve(EntityTable::class),
    val acousticSceneBank: ConcurrentAcousticSceneBank = ConcurrentAcousticSceneBank(),
    val acousticBlockData: AcousticBlockData = AcousticBlockData.BUILTIN,
)

abstract class EngineMinecraftServer(protected val dependencies: EngineMinecraftServerDependencies) : ServerEventListener {
    val minecraftServer = dependencies.minecraftServer
    val database = connectDatabase(minecraftServer)
    protected val playerStorage = dependencies.playerStorage
    protected val acousticSceneBank = dependencies.acousticSceneBank
    protected val acousticBlockData = dependencies.acousticBlockData
    protected val config = dependencies.config
    val entityTable = dependencies.entityTable.server
    val acousticSimulator = MinecraftAcousticManager(this, dependencies.entityTable, acousticSceneBank, acousticBlockData)
    val engine = EngineServer(
        config.server,
        playerStorage,
        acousticSimulator,
        this,
        dependencies.namespacedStorage,
        minecraftServer.runningThread,
        minecraftServer.getWorldPath(LevelResource.ROOT).toFile(),
        database
    )

    protected abstract val transportContext: ServerTransportContext
    protected open val connectionManager: ServerConnectionManager? = null
    val timers = SaveTimers(
        SaveTimers.Counter(config.itemAutosavePeriod * 20),
        SaveTimers.Counter(config.itemAutosavePeriod * 20, (config.itemAutosavePeriod * 0.5).toInt())
    )
    val luaContext: LuaContext = dependencies.luaContext

    open fun wrapItemStack(owner: EnginePlayer, itemId: ItemId, itemStack: ItemStack): EngineItem = with(owner.world) {
        val item = instantiateItem(
            this,
            engine.namespacedStorage.items[itemId] ?: error("Префаб предмета $itemId не найден"),
            engine.itemStorage
        )
        wrapEngineItemStack(item, itemStack)
        return item
    }

    open fun createItemStack(owner: EnginePlayer, itemId: ItemId, itemStackHandler: (ItemStack, EngineItem) -> Unit): EngineItem {
        val itemStack = ITEM_STACK_MATERIAL.copy()
        return wrapItemStack(owner, itemId, itemStack)
            .also { itemStackHandler(itemStack, it) }
    }

    open fun tick() {
        if (!minecraftServer.isRunning) return
        engine.preUpdate()
        val players = engine.playerStorage.getAll()
        updateServerMinecraftSystems(this, dependencies.entityTable, players, connectionManager)
        engine.listWorlds().forEach { world -> world.players.forEach { player -> updatePlayerOwnedItems(world, player) } }
        engine.update()
        updateBulletsMinecraft(engine.defaultWorld, minecraftServer.overworld())
        engine.listWorlds().forEachWithContext({ it }) { world ->
            updatePlayerScriptSystem()
            updateScriptLightSystem()
            world.updateVoxelEvents(engine.handler)
            world.clearEvents()
            updateUnloadSystem(world, timers)
            updateSaveSystem(this@EngineMinecraftServer)
        }
        timers.items.tick()
        timers.containers.tick()
        engine.postUpdate()
    }

    open fun run() {
        Injector.register<PlayerPermissionsProvider>(MinecraftPermissionProvider(entityTable))
        Injector.register<ServerTransportContext>(transportContext)
        Injector.register(engine.itemStorage)
        Injector.register<ItemAccess>(engine.itemStorage)
        Injector.register(engine.globals.movementSettings)
        applyConfigCatching(config)
        val compilationResult = dependencies.compilationResult
        luaContext.setupGame(LuaRuntimeDependencies(playerStorage, engine.worlds))
        engine.loadContents(luaContext, compilationResult)
        minecraftServer.allLevels.forEach {
            val id = it.engine
            val world = world(id, engine.thread, engine.namespacedStorage) { chunkPos ->
                it.chunkSource.chunkMap.getPlayers(ChunkPos(chunkPos.x, chunkPos.z), false)
                    .mapNotNull { entity -> entityTable.getPlayer(entity) }
            }
            world.registerScriptComponents(engine.namespacedStorage)
            with(world) { world.worldState.copyState(engine.loadWorldComponents(world)) }
            engine.addWorld(world)
            dependencies.entityTable.setWorld(id, it)
            luaContext.loadWorld(world)
        }
        engine.run()
    }

    fun recompileEngineContents(player: EnginePlayer?) {
        try {
            engine.loadContents(luaContext)
        } catch (e: Throwable) {
            CONFIG_LOGGER.error("При компиляции ресурсов возникла ошибка", e)
            if (player != null) {
                engine.handler.onServerNotification(player, Notification.COMPILATION_ERROR, false)
            }
        }
    }

    open fun disable() = runBlocking {
        engine.allWorlds().forEach { database.saveItemsBlocking(it) }
        engine.stop()
    }

    open fun onJoinPlayer(entity: ServerPlayer) {}

    open fun onLeavePlayer(entity: ServerPlayer) {
        val player = entityTable.getPlayer(entity) ?: return
        engine.destroyPlayer(player)
        entityTable.removePlayer(entity)
        timers.items.activate()
    }

    context(world: World)
    override fun onPlayerInstantiated(player: EnginePlayer) {
        val entity = minecraftServer.playerList.getPlayer(player.id.value) ?: return
        entityTable.setPlayer(entity, player)
        with(luaContext) {
            player.prepareLuaScriptComponents()
            player.entityId.setComponent(player.require<Location>())
        }
    }

    override fun onChatMessage(message: IncomingMessage) {}

    override fun onCompiled(contents: NamespacedStorage) {
        val commandManager = minecraftServer.commands
        commandManager.dispatcher.registerIntentCommands(engine.namespacedStorage, handler=engine.handler)
        minecraftServer.players.forEach { commandManager.sendCommands(it) }
    }

    fun onBlockBreak(pos: BlockPos, world: Level) {
        acousticSimulator.removeBlock(pos, world)
        val engineWorld = engine.getWorld(world.engine)
        val voxelPos = ImmutableVoxelPos(pos.x, pos.y, pos.z)
        engineWorld.chunkStorage.removeVoxel(voxelPos)
    }

    fun onBlockAdd(player: EnginePlayer?, pos: BlockPos, state: BlockState, world: Level) {
        acousticSimulator.updateBlock(state, pos, world)
        engine.callbacks.executePlaceVoxelCallback(player, engine.getWorld(world), pos.voxelPos(), state)
    }

    fun onChunkUnload(world: Level, chunk: ChunkAccess) {
        val pos = chunk.pos.engineChunkPos()
        acousticSimulator.unloadChunkAsync(world.engine, chunk)
        engine.handler.onChunkUnload(pos)
        val engineWorld = engine.getWorld(world)
        val engineChunk = engineWorld.chunkStorage.getChunk(pos) ?: return
        val componentManager = engineWorld.componentManager
        val savableComponentArrays = componentManager.listArrays().filter { it.meta.savable }
        engineWorld.chunkStorage.removeChunk(pos)
        saveChunkAsync(
            engine,
            pos,
            engineChunk.decals.toMap(),
            engineChunk.hints.toMap(),
            engineChunk.dynamicVoxels.mapValues { (_, entity) ->
                savableComponentArrays.mapNotNull {
                    with(engineWorld) { it.componentOf(entity)?.toSnapshotDto() }
                }
            }
        )
    }

    fun onWorldUnload(world: Level) {
        val engineWorld = engine.getWorld(world)
        engine.saveWorld(engineWorld)
    }
}

fun EngineServer.serverMinecraftPlayerLoadSettings(
    entity: Player,
    playerId: PlayerId,
    developerModeStatus: DeveloperModeStatus,
    notifications: List<Notification>
): PlayerLoadSettings {
    assertOnThread()
    val stacks = entity.ownedItems

    return PlayerLoadSettings(
        playerId,
        stacks.mapNotNull { it.engine()?.uuid },
        notifications,
        entity.position().engine(),
        entity.name.string,
        developerModeStatus,
        getWorld(entity.level().engine),
    )
}