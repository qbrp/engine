package org.lain.engine.mc.server

import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.ProblemReporter
import net.minecraft.world.ItemStackWithSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.destroy
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.createItem
import org.lain.engine.mc.*
import org.lain.engine.mc.commands.ScriptPathSuggestionProvider
import org.lain.engine.mc.commands.registerIntentCommands
import org.lain.engine.mc.commands.updateCommandInvokeSystem
import org.lain.engine.player.*
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.*
import org.lain.engine.script.lua.*
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerPlatform
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
import org.lain.engine.world.*

data class EngineMinecraftServerDependencies(
    val minecraftServer: MinecraftServer,
    val luaScriptEngine: LuaScriptEngine,
    val compilationResult: CompilationResult,
    val config: ServerConfig = loadOrCreateServerConfig(),
    val namespacedStorage: NamespacedStorageAccess,
    val playerStorage: PlayerStorage = ConcurrentStorage(),
    val entityTable: EntityTable = Injector.resolve(EntityTable::class),
    val acousticSceneBank: ConcurrentAcousticSceneBank = ConcurrentAcousticSceneBank(),
    val acousticBlockData: AcousticBlockData = AcousticBlockData.BUILTIN,
    val isReplay: Boolean = minecraftServer.isReplayServer,
)

abstract class EngineMinecraftServer(protected val dependencies: EngineMinecraftServerDependencies) :
    ServerPlatform {
    val minecraftServer = dependencies.minecraftServer
    val database = connectDatabase(minecraftServer)
    protected val playerStorage = dependencies.playerStorage
    protected val acousticSceneBank = dependencies.acousticSceneBank
    protected val acousticBlockData = dependencies.acousticBlockData
    protected val config = dependencies.config
    val entityTable = dependencies.entityTable.server
    val acousticSimulator =
        MinecraftAcousticManager(this, dependencies.entityTable, acousticSceneBank, acousticBlockData)
    val luaScriptEngine: LuaScriptEngine = dependencies.luaScriptEngine
    val timers = SaveTimers(
        SaveTimers.Counter(config.itemAutosavePeriod * 20),
        SaveTimers.Counter(config.itemAutosavePeriod * 20, (config.itemAutosavePeriod * 0.5).toInt())
    )
    val engine = EngineServer(
        config.server,
        playerStorage,
        acousticSimulator,
        this,
        dependencies.namespacedStorage,
        minecraftServer.runningThread,
        dependencies.isReplay,
        minecraftServer.getWorldPath(LevelResource.ROOT).toFile(),
        database,
        luaScriptEngine,
        timers,
    )
    private val minecraftSystem = MinecraftSystem(
        dependencies.entityTable,
        this,
    )

    protected abstract val transportContext: ServerTransportContext
    open val connectionManager: ServerConnectionManager? = null

    context(world: World)
    open fun wrapItemStack(itemId: ItemId, itemStack: ItemStack): EngineItem {
        val prefab = engine.namespacedStorage.items[itemId] ?: error("Префаб предмета $itemId не найден")
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

    open fun tick() {
        val entityTableAll = dependencies.entityTable

        val overworld = minecraftServer.overworld()
        engine.defaultWorld.tickVoxelAdapterSystem(overworld)
        engine.defaultWorld.tickVoxelDoorSystem(overworld)

        engine.update(
            prepareData = {
                minecraftSystem.tick(this)
                updateCommandInvokeSystem(entityTableAll)
            },
            updateBulletHitSystem = {
                val level = entityTableAll.getMcWorld(id) as? ServerLevel
                updateBulletsMinecraft(this, level!!)
            },
            updateSaveSystem = {
                updateSaveSystem(this@EngineMinecraftServer)
            }
        )
    }

    open fun run() {
        val compilationResult = dependencies.compilationResult
        if (compilationResult.exceptions.isNotEmpty()) {
            compilationResult.logExceptions()
            throw SetupException(compilationResult.exceptions)
        }

        Injector.register<PlayerPermissionsProvider>(MinecraftPermissionProvider(entityTable))
        Injector.register<ServerTransportContext>(transportContext)
        Injector.register(engine.globals.movementSettings)
        applyConfigCatching(config)
        luaScriptEngine.setupGame(
            LuaScriptEngine.RuntimeDependencies(playerStorage, engine.worlds)
        )
        engine.recompileContents(luaScriptEngine, compilationResult)
        if (compilationResult.exceptions.isNotEmpty()) {
            error("Не удалось скомпилировать ресурсы Engine!")
        }
        minecraftServer.allLevels.forEach {
            val id = it.engine
            val world = world(id, engine.thread, ItemStorage(), engine.namespacedStorage, engine.luaScriptEngine) { chunkPos ->
                it.chunkSource.chunkMap.getPlayers(ChunkPos(chunkPos.x, chunkPos.z), false)
                    .mapNotNull { entity -> entityTable.getPlayer(entity) }
            }
            world.registerComponentTypes(engine.namespacedStorage)
            with(world) { world.state.copyState(engine.loadWorldComponents(world)) }
            engine.addWorld(world)
            dependencies.entityTable.setWorld(id, it)
            luaScriptEngine.loadWorld(world)
        }
        engine.run()
    }

    fun recompileEngineContents(player: EnginePlayer?) {
        try {
            engine.recompileContents(luaScriptEngine)
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
        // onCharacterApplied не срабатывает при первой загрузке игрока, т.к. меню выбора персонажей появляется
        // до его появления мира, из-за чего не срабатывает условие entityTable.getEntity(player) ?: return@execute
        // Повторно вызываем метод принятия персонажа после инстанцирования игрока в мире
        player.get<AppliedCharacter>()?.let { (character) ->
            onCharacterApplied(player, character)
        }
    }

    override fun serializeInventory(player: EnginePlayer): String {
        val entity = entityTable.getEntity(player.id)!!
        val output = TagValueOutput.createWithContext(
            ProblemReporter.ScopedCollector(org.lain.engine.storage.LOGGER),
            minecraftServer.registries().compositeAccess()
        )
        entity.inventory.save(output.list("Inventory", ItemStackWithSlot.CODEC))
        val tag = output.buildResult()
        return tag.toString()
    }

    override fun clearInventory(player: EnginePlayer) {
        val entity = entityTable.getEntity(player.id)!!
        entity.inventory.clearContent()
    }

    override fun openInventory(player: EnginePlayer, inventory: SerializedInventory) {
        val entity = entityTable.getEntity(player.id) ?: minecraftServer.getPlayer(player.id)!!
        val output = TagValueInput.create(
            ProblemReporter.ScopedCollector(org.lain.engine.storage.LOGGER),
            minecraftServer.registries().compositeAccess(),
            TagParser.parseCompoundFully(inventory)
        )
        entity.inventory.load(output.listOrEmpty("Inventory", ItemStackWithSlot.CODEC))
    }

    override fun onCompiled(contents: NamespacedStorage) {
        val commandManager = minecraftServer.commands
        commandManager.dispatcher.registerIntentCommands(engine.namespacedStorage, handler = engine.handler)
        ScriptPathSuggestionProvider.onScriptsCompiled()
        minecraftServer.players.forEach { commandManager.sendCommands(it) }
    }

    override fun onCharacterApplied(player: EnginePlayer, character: EngineCharacter) {
        engine.execute {
            val entity = entityTable.getEntity(player) ?: return@execute
            if (GENDER_MOD_AVAILABLE) {
                syncPlayerGenderConfig(entity, character.profile.biologicalSex, character.profile.genderParams)
            }
        }
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
                    with(engineWorld) {
                        val component = it.componentOf(entity)?.toSnapshotDto()
                        entity.destroy() // сделать в будущем проверку владения
                        component
                    }
                }
            }
        )
    }

    fun onWorldUnload(world: Level) {
        val engineWorld = engine.worlds[world.engine] ?: return
        engine.saveWorld(engineWorld)
    }
}

fun EngineServer.serverMinecraftPlayerLoadSettings(
    entity: Player,
    playerId: PlayerId,
    developerModeStatus: DeveloperModeStatus = DeveloperModeStatus(),
    notifications: List<Notification> = mutableListOf(),
): PlayerLoadSettings {
    assertOnThread()
    val stacks = entity.ownedItems

    return PlayerLoadSettings(
        playerId,
        stacks.mapNotNull {
            val reference = it.engine()
            if (reference?.version != CURRENT_ITEM_VERSION) {
                null
            } else {
                reference.uuid
            }
        },
        notifications,
        entity.position().engine(),
        entity.name.string,
        developerModeStatus,
        getWorld(entity.level().engine),
        entity.isReplayViewer,
        globals.savePath.playerData.parsePersistentPlayerData(playerId),
        entity.enginePlayerMode
    )
}