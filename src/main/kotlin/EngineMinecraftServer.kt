package org.lain.engine

import kotlinx.coroutines.*
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import org.lain.engine.chat.IncomingMessage
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAccess
import org.lain.engine.item.ItemId
import org.lain.engine.mc.*
import org.lain.engine.player.*
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerEventListener
import org.lain.engine.storage.*
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.util.file.applyConfigCatching
import org.lain.engine.util.file.loadContents
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.injectValue
import org.lain.engine.util.require
import org.lain.engine.world.ImmutableVoxelPos
import org.lain.engine.world.location
import org.lain.engine.world.world

data class EngineMinecraftServerDependencies(
    val minecraftServer: MinecraftServer,
    val playerStorage: PlayerStorage = PlayerStorage(),
    val entityTable: EntityTable = Injector.resolve(EntityTable::class),
    val acousticSceneBank: ConcurrentAcousticSceneBank = ConcurrentAcousticSceneBank(),
    val acousticBlockData: AcousticBlockData = AcousticBlockData.BUILTIN,
    val acousticSimulator: MinecraftAcousticManager = MinecraftAcousticManager(entityTable, acousticSceneBank, acousticBlockData),
)

abstract class EngineMinecraftServer(protected val dependencies: EngineMinecraftServerDependencies) : ServerEventListener {
    val minecraftServer = dependencies.minecraftServer
    val database = connectDatabase(minecraftServer)
    protected val playerStorage = dependencies.playerStorage
    protected val acousticSceneBank = dependencies.acousticSceneBank
    protected val config = loadOrCreateServerConfig()
    val entityTable = dependencies.entityTable.server
    val acousticSimulator = dependencies.acousticSimulator
    val engine = EngineServer(
        config.server,
        playerStorage,
        acousticSimulator,
        this,
        minecraftServer.thread,
        minecraftServer.getSavePath(WorldSavePath.ROOT).toFile()
    )

    private val autosaveTimer = ItemAutosaveTimer(config.itemAutosavePeriod * 1000, engine.itemStorage, database)
    private val unloadTimer = UnloadInactiveItemsTimer(config.itemAutosavePeriod / 2 * 1000, engine.itemStorage, database, engine)
    protected val itemLoader = ItemLoader(this)

    protected abstract val transportContext: ServerTransportContext

    open fun wrapItemStack(owner: EnginePlayer, itemId: ItemId, itemStack: ItemStack): EngineItem {
        val item = engine.createItem(owner.location, itemId)
        wrapEngineItemStack(item, itemStack)
        return item
    }

    open fun createItemStack(owner: EnginePlayer, itemId: ItemId, itemStackHandler: (ItemStack, EngineItem) -> Unit): EngineItem {
        val itemStack = ITEM_STACK_MATERIAL.copy()
        return wrapItemStack(owner, itemId, itemStack)
            .also { itemStackHandler(itemStack, it) }
    }

    open fun tick() {
        val players = engine.playerStorage.getAll()
        updateServerMinecraftSystems(this, entityTable, players, itemLoader)
        engine.update()
        updateBullets(engine.defaultWorld, minecraftServer.overworld)
        autosaveTimer.tick()
        unloadTimer.tick()
    }

    open fun run() {
        Injector.register<RaycastProvider>(MinecraftRaycastProvider(injectValue()))
        Injector.register<PlayerPermissionsProvider>(MinecraftPermissionProvider(entityTable))
        Injector.register<ServerTransportContext>(transportContext)
        Injector.register(engine.itemStorage)
        Injector.register<ItemAccess>(engine.itemStorage)
        Injector.register(engine.globals.movementSettings)
        applyConfigCatching(config)
        engine.loadContents()
        minecraftServer.worlds.forEach {
            val id = it.engine
            engine.addWorld(world(id))
            dependencies.entityTable.setWorld(id, it)
        }
        engine.run()
    }

    open fun disable() = runBlocking {
        database.saveItemPersistentDataBatch(engine.itemStorage.getAll().mapPersistentData())
        engine.stop()
    }

    open fun onJoinPlayer(entity: ServerPlayerEntity) {}

    open fun onLeavePlayer(entity: ServerPlayerEntity) {
        if (entity.networkHandler.isConnectionOpen) {
            return
        }
        val player = entityTable.getPlayer(entity) ?: return
        engine.playerService.destroy(player)
        entityTable.removePlayer(entity)
        unloadTimer.activate()
    }

    override fun onPlayerInstantiated(player: EnginePlayer) {
        val entity = minecraftServer.playerManager.getPlayer(player.id.value) ?: return
        entityTable.setPlayer(entity, player)
    }

    override fun onChatMessage(message: IncomingMessage) {}

    fun onBlockBreak(pos: BlockPos, world: World) {
        acousticSimulator.removeBlock(pos, world)
        val world = engine.getWorld(world.engine)
        val voxelPos = ImmutableVoxelPos(pos.x, pos.y, pos.z)
        world.chunkStorage.removeVoxel(voxelPos)
        engine.handler.onVoxelDestroy(world, voxelPos)
    }

    fun onBlockAdd(block: BlockState, pos: BlockPos, world: World) {
        acousticSimulator.updateBlock(block, pos, world)
    }

    fun onPlayerBlockInteraction(pos: BlockPos, state: BlockState, world: World) {
        onBlockAdd(state, pos, world)
    }

    fun onChunkUnload(world: World, chunk: Chunk) {
        val pos = chunk.pos.engine()
        acousticSimulator.onChunkUnload(world.engine, chunk)
        engine.handler.onChunkUnload(pos)
        val engineChunk = engine.getWorld(world).chunkStorage.getChunk(pos) ?: return
        saveChunk(engine, pos, engineChunk.decals.toMap(), engineChunk.hints.toMap())
    }
}

fun serverMinecraftPlayerInstance(
    server: EngineMinecraftServer,
    entity: PlayerEntity,
    playerId: PlayerId,
): EnginePlayer {
    val engine = server.engine
    val persistentPlayerData = parsePersistentPlayerData(playerId)
    val defaults = engine.globals.defaultPlayerAttributes
    val stacks = entity.inventory.mainStacks

    return serverPlayerInstance(
        PlayerInstantiateSettings(
            engine.getWorld(entity.entityWorld.engine),
            entity.entityPos.engine(),
            DisplayName(
                Username(entity.name),
                persistentPlayerData?.customName?.toDomain(entity.name.string)
            ),
            MovementStatus(
                intention = persistentPlayerData?.speedIntention ?: MovementStatus.DEFAULT_INTENTION,
                stamina = persistentPlayerData?.stamina ?: MovementStatus.DEFAULT_STAMINA
            ),
            PlayerAttributes(),
            Spectating(),
            GameMaster(),
            stacks
                .mapNotNull { it.engineItem() }
                .toSet()
        ),
        persistentPlayerData,
        defaults,
        playerId
    )
}

suspend fun prepareServerMinecraftPlayer(server: EngineMinecraftServer, entity: PlayerEntity, player: EnginePlayer) = withContext(Dispatchers.IO) {
    val items = mutableListOf<Deferred<EngineItem?>>()
    for (itemStack in entity.inventory.mainStacks) {
        val reference = itemStack.engine() ?: continue
        if (reference.getItem() == null) {
            items.add(
                async { server.loadItemStack(itemStack, player) }
            )
        }
    }

    player.require<PlayerInventory>().items.addAll(items.awaitAll().filterNotNull())
}