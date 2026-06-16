package org.lain.engine.client

import kotlinx.coroutines.runBlocking
import org.lain.cyberia.ecs.*
import org.lain.engine.client.chat.ChatBubbleList
import org.lain.engine.client.chat.ClientEngineChatManager
import org.lain.engine.client.chat.PlayerVocalRegulator
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.control.MovementManager
import org.lain.engine.client.handler.*
import org.lain.engine.client.render.WARNING
import org.lain.engine.client.render.updateShootShakeSystem
import org.lain.engine.client.script.updateClientServerboundChannelSystem
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.client.util.SPECTATOR_NOTIFICATION
import org.lain.engine.client.util.processSoundPlayKeys
import org.lain.engine.container.clearAssignItemsOperations
import org.lain.engine.container.updateContainerOperationSystem
import org.lain.engine.container.updatePlayerContainerSystem
import org.lain.engine.container.updateSlotContainers
import org.lain.engine.item.EngineItem
import org.lain.engine.item.handleWriteableInteractions
import org.lain.engine.item.updateFireTimeSystem
import org.lain.engine.item.updateRecoilSystem
import org.lain.engine.player.*
import org.lain.engine.script.Callbacks
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.flushServerboundChannelComponents
import org.lain.engine.script.lua.prepareLuaScriptComponents
import org.lain.engine.script.lua.adaptScriptPlayerComponents
import org.lain.engine.script.lua.adaptScriptLightComponents
import org.lain.engine.script.lua.adaptScriptNetworkingComponents
import org.lain.engine.script.registerScriptComponents
import org.lain.engine.script.scriptContext
import org.lain.engine.server.ServerId
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.toDomainSuspend
import org.lain.engine.transport.packet.*
import org.lain.engine.util.WARNING_COLOR
import org.lain.engine.world.*
import java.util.*

class GameSession(
    val server: ServerId,
    setup: ClientboundSetupData,
    world: ClientboundWorldData,
    player: ServerPlayerData,
    val handler: ClientHandler,
    val client: EngineClient,
) {
    val itemStorage = ClientItemStorage()
    val world = World(
        world.id,
        thread = client.thread,
        isClient = true,
        namespacedStorage = namespacedStorage,
        itemStorage = itemStorage
    )

    val renderer = client.renderer
    val chatEventBus = client.chatEventBus
    var synchronizationRadius: Int = setup.settings.synchronizationRadius
    var playerDesynchronizationThreshold: Int = setup.settings.playerDesynchronizationThreshold

    var extendArm = false
    set(value) {
        handler.onArmStatusUpdate(value)
        mainPlayer.extendArm = value
        field = value
    }

    var acousticDebugVolumes = listOf<Pair<VoxelPos, Float>>()
    val playerStorage = ClientPlayerStorage()
    val movementManager = MovementManager(this)
    val chatBubbleList = ChatBubbleList(client.options)
    val chatManager = ClientEngineChatManager(
        chatEventBus,
        client,
        this,
        setup.settings.chat,
    )

    var movementDefaultAttributes = setup.settings.defaultAttributes.movement
    var movementSettings = setup.settings.movement
    val vocalRegulator = PlayerVocalRegulator(
        PlayerVolume(player.volume, player.maxVolume, player.baseVolume),
        this
    )
    val mainPlayer = mainClientPlayerInstance(player.id, this.world, player, DeveloperModeStatus(client.developerMode, client.acousticDebug))
    var ticks = 0L
        private set
    val namespacedStorage get() = client.namespacedStorage
    val luaContext get() = client.luaContext ?: error("Lua context is not initialized")
    var soundsToBroadcast = LinkedList<SoundBroadcast>()
    var callbacks: Callbacks = Callbacks()
    val endTickTaskExecutor = TaskExecutor()

    init {
        applyCompilation(client.compilationResult ?: error("Compilation is not initialized"))

        val items = (player.items + player.equipment.values).associateBy { it.persistentId }
        preloadPlayerItems(items)
        instantiatePlayer(mainPlayer, player.general, mutableMapOf())

        client.eventBus.onMainPlayerInstantiated(client, this, mainPlayer)
        client.renderer.setupGameSession(this)
        setup.playerList.players.forEach { instantiateLowDetailedPlayer(it) }
    }

    private fun preloadPlayerItems(items: Map<PersistentId, ClientboundItemData>) = with(world) {
        val itemEntities = items.mapValues { (_, item) -> item to addEntity() }
        runBlocking {
            itemEntities.forEach { (persistentId, pair) ->
                val (clientboundItemData, itemEntity) = pair
                itemEntity.copyState(
                    clientboundItemData.components.toDomainSuspend {
                        toDomainSuspend(
                            componentLoadSettings,
                            { persistentId ->
                                itemEntities[persistentId]?.second ?: playerJoinException("Предмет $persistentId требует несуществующую связь $persistentId")
                            }
                        )
                    }
                )
            }
        }
    }

    fun applyCompilation(result: CompilationResult) {
        world.registerScriptComponents(namespacedStorage)
        result.callbacks?.let { callbacks = it }
        luaContext.setupClientGameSession(this)

        val exceptions = result.exceptions
        if (exceptions.isNotEmpty()) {
            val line1 = if (exceptions.size == 1) "Возникла 1 ошибка" else "Возникло ${exceptions.size} ошибок"
            client.applyLittleNotification(
                LittleNotification(
                    "Сбой компиляции контента",
                    "$line1. Проверьте консоль для более подробной информации.",
                    WARNING_COLOR,
                    WARNING,
                    lifeTime = 240
                )
            )
            for (exception in exceptions) {
                exception.log()
            }
        }
        onContentsUpdated()
    }

    fun recompile() {
        try {
            applyCompilation(client.compileScripts())
        } catch (e: Exception) {
            client.applyLittleNotification(
                LittleNotification(
                    "Ошибка компиляции клиента",
                    "${e.message ?: "Неизвестная ошибка"}<newline>Проверьте консоль для более подробной информации.",
                    WARNING_COLOR,
                    WARNING,
                    lifeTime = 240
                )
            )
        }
    }

    fun onContentsUpdated() {
        client.audioManager.invalidateCache()
        client.eventBus.onContentsUpdate()
    }

    fun tick() = with(world) {
        ticks++
        chatManager.tick()

        val itemAccess = this@GameSession.itemStorage
        val players = playerStorage.getAll()
        world.players.clear()
        world.players.addAll(players)

        movementManager.stamina = mainPlayer.stamina
        if (mainPlayer.has<SpawnMark>()) {
            client.removeLittleNotification(SPECTATOR_NOTIFICATION)
        }

        for (player in players) {
            if (player.pos.squaredDistanceTo(mainPlayer.pos) > synchronizationRadius * synchronizationRadius) {
                player.isLowDetailed = true
                continue
            } else {
                player.isLowDetailed = false
            }

            updatePlayerMovement(player, movementDefaultAttributes, movementSettings, true)
            updatePlayerVerbLookup(player, false)
            player.handle<InteractionComponent>() {
                handlePlayerInventoryInteractions(player)
                handleWriteableInteractions(player)
//                handleGunInteractions(player, true)
//                handleFlashlightInteractions(player)
//                handlePlayerEquipmentInteractionProgression(player)
                finishPlayerInteraction(player)

                val processedInteraction = player.get<InteractionComponent>()
                if (this != processedInteraction) {
                    handler.processedInteractions.add(this.id)
                }
            }

            updateHearing(player)
        }
        updateFireTimeSystem()
        updateRecoilSystem()
        updateShootShakeSystem(mainPlayer, client.camera)

        tickNarrations(mainPlayer)

        chatBubbleList.cleanup()
        chatBubbleList.tick(mainPlayer)
        val sounds = processWorldSounds(namespacedStorage, world)
        processSoundPlayKeys(LinkedList(sounds + soundsToBroadcast), handler, client.audioManager)
        soundsToBroadcast.clear()
        updateSlotContainers(world)
        updateContainerOperationSystem()
        updatePlayerContainerSystem()
        clearAssignItemsOperations(world)

        // Scripts
        adaptScriptPlayerComponents()
        tickCallbacks(callbacks)
        adaptScriptLightComponents()
        updateClientServerboundChannelSystem(handler)
        updateVoxelEvents(null)

        flushServerboundChannelComponents()

        endTickTaskExecutor.flush()
    }

    fun loadChunk(pos: EngineChunkPos, chunk: EngineChunk) {
        world.chunkStorage.setChunk(pos, chunk)
        client.eventBus.onChunkLoad(pos, chunk)
    }

    fun instantiateLowDetailedPlayer(data: GeneralPlayerData): EnginePlayer {
        val player = lowDetailedClientPlayerInstance(data.playerId, world, data)
        instantiatePlayer(player, data)
        return player
    }

    fun instantiatePlayer(
        player: EnginePlayer,
        data: GeneralPlayerData,
        equipment: Map<EquipmentSlot, EngineItem> = emptyMap(),
    ) {
        playerStorage.add(player.id, player)
        with(world) {
            with(luaContext) { player.prepareLuaScriptComponents() }
            player.prepareContainers(data.equipmentContainer, player.location, equipment)
            player.entityId.setComponent(Player(player))
            player.entityId.setComponent(player.location)
            player.entityId.setComponent(player.require<Location>())
            callbacks.playerInstantiate.execute(player.scriptContext)
        }
    }

    fun destroy() {
        playerStorage.clear()
        client.renderer.invalidate()
        handler.disable()
    }

    fun getPlayer(id: PlayerId) = playerStorage.get(id)
}