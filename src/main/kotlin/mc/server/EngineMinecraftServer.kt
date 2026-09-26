package org.lain.engine.mc.server

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.Vec3
import org.lain.engine.data.*
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.createItem
import org.lain.engine.mc.*
import org.lain.engine.mc.commands.ScriptPathSuggestionProvider
import org.lain.engine.mc.commands.registerOperationCommands
import org.lain.engine.mc.commands.updateCommandInvokeSystem
import org.lain.engine.mc.compat.GENDER_MOD_AVAILABLE
import org.lain.engine.mc.compat.isReplayServer
import org.lain.engine.mc.compat.isReplayViewer
import org.lain.engine.mc.compat.syncPlayerGenderConfig
import org.lain.engine.mc.ecs.*
import org.lain.engine.player.*
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.compilation.CompilationFailedException
import org.lain.engine.script.compilation.loadBuild
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerPlatform
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.Injector
import org.lain.engine.util.file.ServerConfig
import org.lain.engine.util.file.applyConfigCatching
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.world.ImmutableVoxelPos
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

abstract class EngineMinecraftServer(val dependencies: Dependencies) : ServerPlatform {
    val minecraftServer = dependencies.minecraftServer
    val database = connectDatabase(minecraftServer)
    val access = ServerMinecraftAccess(this)
    protected val playerStorage = dependencies.playerStorage
    protected val acousticSceneBank = dependencies.acousticSceneBank
    protected val acousticBlockData = dependencies.acousticBlockData
    protected val config = dependencies.config
    val worldTable = dependencies.worldTable
    val acousticSimulator =
        MinecraftAcousticManager(
            this,
            dependencies.worldTable,
            acousticSceneBank,
            acousticBlockData
        )
    val luaScriptEngine: LuaScriptEngine = dependencies.luaScriptEngine
    val timers = SaveTimers(
        SaveTimers.Counter(config.itemAutosavePeriod * 20),
        SaveTimers.Counter(
            config.itemAutosavePeriod * 20,
            (config.itemAutosavePeriod * 0.5).toInt()
        )
    )
    val miniMessageAudiences = FabricServerAudiences.of(minecraftServer)
    val engine = EngineServer(
        config.server,
        playerStorage,
        acousticSimulator,
        this,
        dependencies.namespacedStorage,
        dependencies.moduleManager,
        minecraftServer.runningThread,
        dependencies.isReplay,
        minecraftServer.getWorldPath(LevelResource.ROOT).toFile(),
        database,
        luaScriptEngine,
        timers,
    )
    private val minecraftSystem = MinecraftSystem(this)

    protected abstract val transportContext: ServerTransportContext
    open val connectionManager: ServerConnectionManager? = null

    context(world: World)
    open fun wrapItemStack(itemId: ItemId, itemStack: ItemStack): EngineItem {
        val prefab =
            engine.namespacedStorage.items[itemId] ?: error("Префаб предмета $itemId не найден")
        val item = world.createItem(prefab)
        wrapEngineItemStack(item, itemStack)
        return item
    }

    open fun createItemStack(
        owner: EnginePlayer,
        itemId: ItemId,
        itemStackHandler: (ItemStack, EngineItem) -> Unit
    ): EngineItem = with(owner.world) {
        val itemStack = ITEM_STACK_MATERIAL.copy()
        return wrapItemStack(itemId, itemStack)
            .also { itemStackHandler(itemStack, it) }
    }

    override fun World.prepareData() {
        val level = minecraftServer.allLevels.find { it.engineId == id }!!
        minecraftSystem.tick(this)
        tickVoxelAdapterSystem(level)
        updateCommandInvokeSystem(dependencies.worldTable)
    }

    override fun World.updateBulletHitSystem() {
        val level = dependencies.worldTable.getMcWorld(id) as? ServerLevel
        tickBulletFireDecalSystem(this, level!!)
    }

    override fun World.updateSaveSystem() {
        updateSaveSystem(this@EngineMinecraftServer)
    }

    override fun World.applyData() {
        val level = minecraftServer.allLevels.find { it.engineId == id }!!
        tickVoxelDoorSystem(level)
    }

    open fun tick() {
        engine.update()
    }

    open fun run() {
        Injector.register<ServerTransportContext>(transportContext)
        applyConfigCatching(config)
        luaScriptEngine.setupGame(
            LuaScriptEngine.RuntimeDependencies(engine.simulation)
        )
        engine.loadBuild(dependencies.build)
        minecraftServer.allLevels.forEach { level ->
            val id = level.engineId
            val world = World(
                id,
                engine.simulation,
                playersWatchingChunkProvider = { chunkPos ->
                    level.chunkSource.chunkMap
                        .getPlayers(ChunkPos(chunkPos.x, chunkPos.z), false)
                        .mapNotNull { it.getEngineState() }
                },
                server = engine,
            )
            worldTable.setWorld(id, level)
            world.loadPersistentState(engine)
            engine.simulation.loadWorld(world)
            MinecraftAccessRegistry.register(level, access)
        }
        engine.run()
    }

    fun recompileEngineContents(player: EnginePlayer?) {
        val build = try {
            luaScriptEngine.compileContents().successOrThrow()
        } catch (e: CompilationFailedException) {
            if (player != null) {
                e.log()
                engine.handler.onServerNotification(
                    player, Notification.COMPILATION_ERROR, false
                )
            }
            return
        }
        engine.loadBuild(build)
    }

    open fun disable() = runBlocking {
        LOGGER.info("Сохранение чанков")
        engine.chunkPersistence.close()
        LOGGER.info("Сохранение миров")
        engine.allWorlds().forEach {
            database.saveWorldSnapshot(it.createSaveSnapshot())
        }
        engine.stop()
        MinecraftAccessRegistry.invalidate()
        Injector.unregister<EngineMinecraftServer>()
    }

    open fun onJoinPlayer(entity: ServerPlayer) {
        engine.handler.openConnection(entity.engineId)
    }

    open fun onLeavePlayer(entity: ServerPlayer) {
        engine.handler.closeConnection(entity.engineId)
        val player = entity.getEngineState() ?: return
        engine.destroyPlayer(player)
        timers.items.activate()
    }

    context(world: World)
    override fun onPlayerInstantiated(player: EnginePlayer) {
        val entity = minecraftServer.getPlayer(player.id) ?: return
        player.set(MinecraftPlayer(entity))
    }

    override fun hasPermission(player: EnginePlayer, permission: String): Boolean {
        return player.minecraftEntity.hasPermission(permission)
    }

    override fun serializeInventory(player: EnginePlayer): String {
        val entity = player.minecraftEntity
        val tag = CompoundTag()
        tag.put("Inventory", entity.inventory.save(ListTag()))
        return tag.toString()
    }

    override fun clearInventory(player: EnginePlayer) {
        player.minecraftEntity.inventory.clearContent()
    }

    override fun openInventory(player: EnginePlayer, inventory: SerializedInventory) {
        val entity = player.minecraftEntityNullable ?: minecraftServer.getPlayer(player.id)!!
        val tag = TagParser.parseTag(inventory)
        entity.inventory.load(tag.getList("Inventory", Tag.TAG_COMPOUND.toInt()))
    }

    override fun onCompiled(contents: NamespacedStorage) {
        val commandManager = minecraftServer.commands
        commandManager.dispatcher.registerOperationCommands(
            engine.namespacedStorage.operations.values,
            handler = engine.handler
        )
        ScriptPathSuggestionProvider.onScriptsCompiled()
        minecraftServer.players.forEach { commandManager.sendCommands(it) }
    }

    override fun onCharacterApplied(player: EnginePlayer, character: EngineCharacter) {
        engine.execute {
            if (GENDER_MOD_AVAILABLE) {
                syncPlayerGenderConfig(
                    player.minecraftEntity as ServerPlayer,
                    character.profile.biologicalSex,
                    character.profile.genderParams
                )
            }
        }
    }

    fun onBlockBreak(pos: BlockPos, world: Level) {
        acousticSimulator.removeBlock(pos, world)
    }

    fun onBlockAdd(player: EnginePlayer?, pos: BlockPos, state: BlockState, world: Level) {
        acousticSimulator.updateBlock(state, pos, world)
        engine.simulation.callbacks.executePlaceVoxelCallback(
            player,
            engine.getWorld(world),
            pos.voxelPos(),
            state
        )
    }

    fun onChunkLoad(world: Level, chunk: ChunkAccess) {
        val engineWorld = engine.simulation.worlds[world.engineId] ?: return
        engine.chunkPersistence.loadChunkAsync(engineWorld, chunk.pos.engineChunkPos())
    }

    fun onChunkUnload(world: Level, chunk: ChunkAccess) {
        val pos = chunk.pos.engineChunkPos()
        acousticSimulator.unloadChunkAsync(world.engineId, chunk)
        val engineWorld = engine.getWorld(world)
        val engineChunk = engineWorld.chunkStorage.getChunk(pos) ?: return
        if (!engineChunk.isEmpty()) {
            engine.chunkPersistence.saveChunk(engineWorld, pos, engineChunk)
        }
    }

    fun onWorldUnload(world: Level) {
        val engineWorld = engine.simulation.worlds[world.engineId] ?: return
        engine.saveWorld(engineWorld)
    }

    data class Dependencies(
        val minecraftServer: MinecraftServer,
        val luaScriptEngine: LuaScriptEngine,
        val moduleManager: ModuleManager,
        val build: Build,
        val config: ServerConfig = loadOrCreateServerConfig(),
        val namespacedStorage: NamespacedStorageAccess,
        val playerStorage: PlayerStorage = PlayerStorage(),
        val worldTable: ServerWorldTable = ServerWorldTable(),
        val acousticSceneBank: ConcurrentAcousticSceneBank = ConcurrentAcousticSceneBank(),
        val acousticBlockData: AcousticBlockData = AcousticBlockData.BUILTIN,
        val isReplay: Boolean = minecraftServer.isReplayServer,
    )

    companion object {
        fun serverMinecraftPlayerLoadSettings(
            server: EngineServer,
            entity: Player,
            playerId: PlayerId,
            developerModeStatus: DeveloperModeStatus = DeveloperModeStatus(),
            notifications: List<Notification> = mutableListOf(),
        ): PlayerLoadSettings {
            server.simulation.assertOnThread()
            return serverPlayerLoadSettings(
                server,
                playerId,
                entity.name,
                entity.level().engineId,
                entity.isReplayViewer,
                entity.enginePlayerMode,
                developerModeStatus,
                notifications,
                entity.ownedItems,
                entity.position()
            )
        }

        fun serverPlayerLoadSettings(
            server: EngineServer,
            playerId: PlayerId,
            username: Text,
            worldId: WorldId,
            isReplayViewer: Boolean = false,
            playerMode: PlayerMode = PlayerMode.DEFAULT,
            developerModeStatus: DeveloperModeStatus = DeveloperModeStatus(),
            notifications: List<Notification> = mutableListOf(),
            ownedItems: List<ItemStack> = listOf(),
            position: Vec3 = Vec3(0.0, 0.0, 0.0),
        ): PlayerLoadSettings {
            server.simulation.assertOnThread()

            return PlayerLoadSettings(
                playerId,
                ownedItems.mapNotNull {
                    val reference = it.engine()
                    if (reference?.version != CURRENT_ITEM_VERSION) {
                        null
                    } else {
                        reference.uuid
                    }
                },
                notifications,
                position.engine(),
                username.string,
                developerModeStatus,
                server.getWorld(worldId),
                isReplayViewer,
                playerMode,
            )
        }
    }
}
