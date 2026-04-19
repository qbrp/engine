package org.lain.engine

import kotlinx.coroutines.runBlocking
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.chunk.Chunk
import org.lain.cyberia.ecs.require
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.chat.IncomingMessage
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAccess
import org.lain.engine.item.ItemId
import org.lain.engine.item.instantiateItem
import org.lain.engine.mc.*
import org.lain.engine.player.*
import org.lain.engine.script.loadContents
import org.lain.engine.script.lua.*
import org.lain.engine.script.registerScriptComponents
import org.lain.engine.script.scripts
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerEventListener
import org.lain.engine.storage.*
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.SERVERBOUND_RELOAD_CONTENTS_REQUEST_ENDPOINT
import org.lain.engine.util.ConcurrentStorage
import org.lain.engine.util.Injector
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.file.CONFIG_LOGGER
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.applyConfigCatching
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.world.*
import org.luaj.vm2.lib.jse.JsePlatform

data class EngineMinecraftServerDependencies(
    val minecraftServer: MinecraftServer,
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
    protected val config = loadOrCreateServerConfig()
    val entityTable = dependencies.entityTable.server
    val acousticSimulator = MinecraftAcousticManager(this, dependencies.entityTable, acousticSceneBank, acousticBlockData)
    val engine = EngineServer(
        config.server,
        playerStorage,
        acousticSimulator,
        this,
        minecraftServer.thread,
        minecraftServer.getSavePath(WorldSavePath.ROOT).toFile(),
        database
    )

    protected abstract val transportContext: ServerTransportContext
    protected open val connectionManager: ServerConnectionManager? = null
    private val timers = SaveTimers(
        SaveTimers.Counter(config.itemAutosavePeriod * 20),
        SaveTimers.Counter(config.itemAutosavePeriod * 20, (config.itemAutosavePeriod * 0.5).toInt())
    )
    private val luaDataStorage = LuaDataStorage()
    private val luaGlobals = JsePlatform.standardGlobals()
    protected var luaContext = createLuaContext()

    fun createLuaContext(): LuaContext = LuaContext(
        LuaDependencies(
            luaGlobals,
            luaDataStorage,
            engine.playerStorage,
            engine.worlds,
            engine.namespacedStorage,
            ENGINE_DIR.scripts,
        )
    )

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
        val players = engine.playerStorage.getAll()
        updateServerMinecraftSystems(this, entityTable, players, connectionManager)
        engine.listWorlds().forEach { world -> world.players.forEach { player -> updatePlayerOwnedItems(world, player) } }
        engine.update()
        updateBulletsMinecraft(engine.defaultWorld, minecraftServer.overworld)
        engine.listWorlds().forEach { world ->
            updateScriptComponents(world)
            world.updateVoxelEvents(engine.handler)
            world.clearEvents()
            updateUnloadSystem(world, timers)
            updateSaveSystem(this, world)
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
        engine.loadContents(luaContext)
        minecraftServer.worlds.forEach {
            val id = it.engine
            val world = world(id, engine.thread) { chunkPos ->
                it.chunkManager.chunkLoadingManager.getPlayersWatchingChunk(ChunkPos(chunkPos.x, chunkPos.z), false)
                    .mapNotNull { entity -> entityTable.getPlayer(entity) }
            }
            world.registerScriptComponents(engine.namespacedStorage)
            engine.addWorld(world)
            dependencies.entityTable.setWorld(id, it)
        }
        engine.run()
        SERVERBOUND_RELOAD_CONTENTS_REQUEST_ENDPOINT.registerReceiver { ctx -> onRequestReloadContents(ctx.sender) }
    }

    private fun onRequestReloadContents(playerId: PlayerId) = playerStorage.get(playerId)?.let { player ->
        if (player.hasPermission("reloadenginecontents") || minecraftServer.isRunning) {
            try {
                recompileEngineContents()
            } catch (e: Throwable) {
                CONFIG_LOGGER.error("При компиляции ресурсов возникла ошибка", e)
                engine.handler.onServerNotification(player, Notification.COMPILATION_ERROR, false)
            }
        }
    }

    fun recompileEngineContents() {
        val luaContext = createLuaContext()
        this.luaContext = luaContext
        engine.loadContents(luaContext)
    }

    open fun disable() = runBlocking {
        engine.allWorlds().forEach { database.saveItems(it) }
        engine.stop()
    }

    open fun onJoinPlayer(entity: ServerPlayerEntity) {}

    open fun onLeavePlayer(entity: ServerPlayerEntity) {
        if (entity.networkHandler.isConnectionOpen) {
            return
        }
        val player = entityTable.getPlayer(entity) ?: return
        engine.destroyPlayer(player)
        entityTable.removePlayer(entity)
        timers.items.activate()
    }

    context(world: World)
    override fun onPlayerInstantiated(player: EnginePlayer) {
        val entity = minecraftServer.playerManager.getPlayer(player.id.value) ?: return
        entityTable.setPlayer(entity, player)
        with(luaContext) {
            player.prepareLuaScriptComponents()
            player.entityId.setComponent(player.require<Location>())
        }
    }

    override fun onChatMessage(message: IncomingMessage) {}

    override fun onCompiled(contents: NamespacedStorage) {
        val commandManager = minecraftServer.commandManager
        commandManager.dispatcher.registerIntentCommands(engine.namespacedStorage, handler=engine.handler)
        minecraftServer.playerManager.playerList.forEach { commandManager.sendCommandTree(it) }
    }

    fun onBlockBreak(pos: BlockPos, world: net.minecraft.world.World) {
        acousticSimulator.removeBlock(pos, world)
        val engineWorld = engine.getWorld(world.engine)
        val voxelPos = ImmutableVoxelPos(pos.x, pos.y, pos.z)
        engineWorld.chunkStorage.removeVoxel(voxelPos)?.let { dynamicVoxel -> engineWorld.destroy(dynamicVoxel) }
    }

    fun onBlockAdd(player: EnginePlayer?, pos: BlockPos, state: BlockState, world: net.minecraft.world.World) {
        acousticSimulator.updateBlock(state, pos, world)
        engine.callbacks.executePlaceVoxelCallback(player, engine.getWorld(world), pos.engine(), state,)
    }

    fun onChunkUnload(world: net.minecraft.world.World, chunk: Chunk) {
        val pos = chunk.pos.engine()
        acousticSimulator.onChunkUnload(world.engine, chunk)
        engine.handler.onChunkUnload(pos)
        val engineChunk = engine.getWorld(world).chunkStorage.getChunk(pos) ?: return
        engine.getWorld(world).chunkStorage.removeChunk(pos)
        saveChunk(engine, pos, engineChunk.decals.toMap(), engineChunk.hints.toMap())
    }
}

fun serverMinecraftPlayerLoadSettings(
    entity: PlayerEntity,
    playerId: PlayerId,
    developerModeStatus: DeveloperModeStatus,
    notifications: List<Notification>
): PlayerLoadSettings {
    val stacks = entity.inventory.mainStacks

    return PlayerLoadSettings(
        playerId,
        stacks.mapNotNull { it.engine()?.uuid },
        notifications,
        entity.entityPos.engine(),
        entity.name.string,
        developerModeStatus,
        entity.entityWorld.engine
    )
}

fun PlayerEntity.copyMainStacks() = inventory.mainStacks.map { it.copy() }