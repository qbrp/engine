package org.lain.engine

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import org.lain.engine.chat.IncomingMessage
import org.lain.engine.mc.AcousticBlockData
import org.lain.engine.mc.ConcurrentAcousticSceneBank
import org.lain.engine.mc.EngineItemContext
import org.lain.engine.mc.EngineItemRegistry
import org.lain.engine.mc.engine
import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.MinecraftAcousticManager
import org.lain.engine.mc.MinecraftPermissionProvider
import org.lain.engine.mc.Username
import org.lain.engine.mc.updateMinecraftSystems
import org.lain.engine.player.DisplayName
import org.lain.engine.player.GameMaster
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerInstantiateSettings
import org.lain.engine.player.PlayerPermissionsProvider
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.Spectating
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.playerBaseInputVolume
import org.lain.engine.player.serverPlayerInstance
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerEventListener
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.util.MinecraftRaycastProvider
import org.lain.engine.util.applyConfigCatching
import org.lain.engine.util.compileInventoryTabsConfig
import org.lain.engine.util.compileItems
import org.lain.engine.util.compileItemsCatching
import org.lain.engine.util.loadOrCreateServerConfig
import org.lain.engine.util.parsePersistentPlayerData
import org.lain.engine.world.world

data class EngineMinecraftServerDependencies(
    val minecraftServer: MinecraftServer,
    val playerStorage: PlayerStorage = PlayerStorage(),
    val entityTable: EntityTable = Injector.resolve(EntityTable::class),
    val acousticSceneBank: ConcurrentAcousticSceneBank = ConcurrentAcousticSceneBank(),
    val acousticBlockData: AcousticBlockData = AcousticBlockData.BUILTIN,
    val acousticSimulator: MinecraftAcousticManager = MinecraftAcousticManager(entityTable, acousticSceneBank, acousticBlockData),
)

open class EngineMinecraftServer(
    protected val dependencies: EngineMinecraftServerDependencies,
    protected open val transportContext: ServerTransportContext
) : ServerEventListener {
    protected val entityTable = dependencies.entityTable
    protected val playerStorage = dependencies.playerStorage
    protected val acousticSceneBank = dependencies.acousticSceneBank
    protected val config = loadOrCreateServerConfig()
    val itemContext = EngineItemContext(
        EngineItemRegistry(),
        listOf()
    )
    val acousticSimulator = dependencies.acousticSimulator
    val minecraftServer = dependencies.minecraftServer
    val engine = EngineServer(config.server, playerStorage, acousticSimulator, this, transportContext)

    open fun tick() {
        updateMinecraftSystems(engine, entityTable, engine.playerStorage.getAll())
        engine.update()
    }

    open fun run() {
        Injector.register(MinecraftRaycastProvider(minecraftServer, entityTable))
        Injector.register<PlayerPermissionsProvider>(MinecraftPermissionProvider(entityTable))
        Injector.register<ServerTransportContext>(transportContext)
        Injector.register(itemContext)
        applyConfigCatching(config)
        compileItemsCatching()
        minecraftServer.worlds.forEach {
            val id = it.engine
            engine.addWorld(world(id))
            entityTable.setWorld(id, it)
        }
        engine.run()
    }

    open fun disable() {
        engine.stop()
    }

    open fun onJoinPlayer(entity: ServerPlayerEntity) {}

    open fun onLeavePlayer(entity: ServerPlayerEntity) {
        val player = entityTable.getPlayer(entity) ?: return
        engine.playerService.destroy(player)
        entityTable.removePlayer(entity)
    }

    override fun onPlayerInstantiated(player: Player) {
        val entity = minecraftServer.playerManager.getPlayer(player.id.value) ?: return
        entityTable.setPlayer(entity, player)
    }

    override fun onChatMessage(message: IncomingMessage) {}

    fun onBlockBreak(block: BlockState, pos: BlockPos, world: World) {
        acousticSimulator.removeBlock(pos, world)
    }

    fun onBlockAdd(block: BlockState, pos: BlockPos, world: World) {
        acousticSimulator.updateBlock(block, pos, world)
    }

    fun onPlayerBlockInteraction(entity: PlayerEntity, pos: BlockPos, state: BlockState, world: World) {
        onBlockAdd(state, pos, world)
    }

    fun onChunkUnload(world: World, chunk: Chunk) {
        acousticSimulator.onChunkUnload(world.engine, chunk)
    }
}

fun serverMinecraftPlayerInstance(
    engineServer: EngineServer,
    entity: PlayerEntity,
    playerId: PlayerId,
): Player {
    val persistentPlayerData = parsePersistentPlayerData(playerId)
    val defaults = engineServer.globals.defaultPlayerAttributes

    return serverPlayerInstance(
        PlayerInstantiateSettings(
            engineServer.getWorld(entity.world.engine),
            entity.pos.engine(),
            DisplayName(
                Username(entity.name),
                persistentPlayerData?.customName
            ),
            MovementStatus(
                intention = persistentPlayerData?.speedIntention ?: MovementStatus.DEFAULT_INTENTION,
                stamina = persistentPlayerData?.stamina ?: MovementStatus.DEFAULT_STAMINA
            ),
            PlayerAttributes(),
            Spectating(),
            GameMaster(),
        ),
        persistentPlayerData,
        defaults,
        playerId
    )
}