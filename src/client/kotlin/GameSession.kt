package org.lain.engine.client

import kotlinx.coroutines.runBlocking
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.client.account.CharacterChange
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.chat.ChatBubbleList
import org.lain.engine.client.chat.ClientEngineChatManager
import org.lain.engine.client.chat.PlayerVocalRegulator
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.control.InspectionMode
import org.lain.engine.client.control.MovementManager
import org.lain.engine.client.control.updateInspectionMode
import org.lain.engine.client.handler.*
import org.lain.engine.client.render.SkinSystem
import org.lain.engine.client.render.WARNING
import org.lain.engine.client.render.tickBulletHitSystem
import org.lain.engine.client.render.ui.Workspace
import org.lain.engine.client.render.tickRecoilShakeSystem
import org.lain.engine.client.script.ClientCompilation
import org.lain.engine.client.script.tickEntityRpcQueueSystem
import org.lain.engine.client.util.*
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemStorage
import org.lain.engine.player.*
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.CharacterApplyEvent
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.interaction.PlayerInputMode
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.server.ServerId
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.toDomainSuspend
import org.lain.engine.transport.packet.*
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.Log
import org.lain.engine.util.WARNING_COLOR
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.*

class GameSession(
    val isMultiPlayer: Boolean,
    val server: ServerId,
    setup: ClientboundSetupData,
    world: ClientboundWorldData,
    player: ServerPlayerData,
    val handler: ClientHandler,
    val client: EngineClient,
    val compilation: ClientCompilation,
    compilationResult: CompilationResult,
    val hintState: ClientHintState = ClientHintState(),
) : EngineSimulation.SimulationTickExtension, EngineSimulation.Settings {
    private val systems: ClientPlatform.TickExtension = client.infrastructure.tickExtension
    val luaContext = compilation.luaContext
    val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
    val playerStorage = PlayerStorage()
    val simulation = EngineSimulation(
        isClient = true,
        this,
        this,
        playerStorage,
        namespacedStorage,
        luaContext,
        client.thread,
        PlayerInputMode.Predictive(setOf(player.id), handler)
    )
    val world = World(
        world.id,
        simulation,
    )
    val itemStorage: ItemStorage = this.world.itemStorage

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
    val movementManager = MovementManager(this)
    val chatBubbleList = ChatBubbleList(client.options)
    val chatManager = ClientEngineChatManager(
        chatEventBus,
        client,
        this,
        setup.settings.chat,
    )

    override var movementDefaultAttributes = setup.settings.defaultAttributes.movement
    override var movementSettings = setup.settings.movement
    val skinSystem = SkinSystem(client.skinTextureManager)

    val vocalRegulator = PlayerVocalRegulator(
        PlayerVolume(player.volume, player.maxVolume, player.baseVolume),
        this
    )
    val mainPlayer = mainClientPlayerInstance(
        player.id,
        this.world,
        player,
        DeveloperModeStatus(client.developerMode, client.acousticDebug)
    )
    val ticks
        get() = simulation.ticks

    val endTickTaskExecutor = TaskExecutor()
    var workspaceSavedState: Workspace.SavedState? = null

    var inspectionMode: Boolean = false
        set(value) {
            client.showInpectionModeToggleNotification(value)
            field = value
        }
    val inspection = InspectionMode()
    var characterChange: CharacterChange? = null
        private set

    init {
        applyCompilation(compilationResult)
        simulation.loadWorld(this.world)

        val items = (player.items + player.equipment.values).associateBy { it.persistentId }
        preloadPlayerItems(items)
        instantiatePlayer(mainPlayer, player.general, mutableMapOf())

        client.infrastructure.onMainPlayerInstantiated(client, this, mainPlayer)
        setup.playerList.players.forEach { instantiateLowDetailedPlayer(it) }
        chatManager.updateSettings(setup.settings.chat)

        if (setup.settings.spectateOnJoin) {
            client.showSpectatingNotification()
        }

        player.character?.let { this.world.emitEvent(CharacterApplyEvent(it, mainPlayer.id)) }
        handler.initializeEntitySynchronization(this)
    }

    fun logInMainThread(loggerGetter: context(World) GameSession.(tick: Long) -> Log) {
        val tick = ticks
        client.execute {
            with(world) {
                with(this@GameSession) {
                    EngineLogger.log(loggerGetter(tick))
                }
            }
        }
    }

    fun toggleInspectionMode() {
        inspectionMode = !inspectionMode
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
                                itemEntities[persistentId]?.second
                                    ?: playerJoinException("Предмет $persistentId требует несуществующую связь $persistentId")
                            },
                        )
                    }
                )
            }
        }
    }

    fun applyCompilation(result: CompilationResult) {
        simulation.applyCompilationResult(result)
        luaContext.setupClientGameSession(this)

        val exceptions = result.exceptions
        if (exceptions.isNotEmpty()) {
            val line1 =
                if (exceptions.size == 1) "Возникла 1 ошибка" else "Возникло ${exceptions.size} ошибок"
            client.showNotification(
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
            applyCompilation(luaContext.compileContents())
        } catch (e: Exception) {
            client.showCompilationErrorNotification(e)
        }
    }

    fun onContentsUpdated() {
        client.audioManager.invalidateCache()
        client.infrastructure.onContentsUpdate()
    }

    override fun World.beforeInput() = with(systems) {
        tickDataPrepareSystem()
        tickPlayerLowDetailedSystem(mainPlayer, synchronizationRadius)
    }

    override fun World.afterInput() {
        tickActionSyncSystem(handler)
    }

    override fun World.afterInteractions() {
        tickProcessedActions(handler)
        handler.endInteractionPrediction()
    }

    override fun World.afterOperations() = with(systems) {
        tickEntityRpcQueueSystem(handler)
        tickBulletFireSystem()
        tickBulletHitSystem(client.camera)
        tickRecoilShakeSystem(mainPlayer, client.camera)
        processWorldSounds(namespacedStorage, client.audioManager)
        chatBubbleList.tick(mainPlayer)

        updateVoxelEvents(null)
        handleHintEvents()
        client.infrastructure.getHitResultVoxelPos()?.let {
            updateInspectionMode(inspection, inspectionMode, it)
        }

        skinSystem.tick(this@afterOperations)
        tickDataApplySystem()
    }

    fun removePlayer(player: EnginePlayer) {
        simulation.preparePlayerDestroy(player)
        simulation.destroyPlayer(player)
        client.infrastructure.onPlayerDestroy(client, player.id)
        skinSystem.removePlayerFromCache(player.id)
    }

    fun tick() {
        chatManager.tick()

        movementManager.stamina = mainPlayer.stamina
        if (mainPlayer.has<SpawnMark>()) {
            client.removeLittleNotification(SPECTATOR_NOTIFICATION)
        }

        simulation.tick()

        endTickTaskExecutor.flush()
    }

    fun viewEntityDebug(entity: EntityId) = with(world) {
        val persistentId = entity.requireComponent<PersistentIdComponent>().id
        handler.onEntityDebugView(persistentId)
        client.infrastructure.onEntityDebugView(this@GameSession)
    }

    fun loadChunk(pos: EngineChunkPos, chunk: EngineChunk) {
        world.chunkStorage.setChunk(pos, chunk)
        client.infrastructure.onChunkLoad(pos, chunk)
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
    ) = with(player.world) {
        player.prepareContainers(data.equipmentContainer, player.location, equipment)
        simulation.instantiatePlayer(player)
    }

    fun openCharacterSelectionMenu() {
        simulation.assertOnThread()
        if (characterChange != null) return

        val appliedCharacter = mainPlayer.get<AppliedCharacter>()?.character
        val selectedLook = mainPlayer.get<SelectedLook>()?.look
        val currentPlayerCharacter = if (appliedCharacter != null && selectedLook != null) {
            CharacterSelection.CurrentCharacter(appliedCharacter, selectedLook)
        } else {
            null
        }
        val change = CharacterChange(this, currentPlayerCharacter)
        characterChange = change
        change.start().invokeOnCompletion {
            client.execute { characterChange = null }
        }
    }

    fun destroy() {
        characterChange?.cancel()
        characterChange = null
        client.renderer.invalidate()
        handler.disable(this)
        client.skinTextureManager.close()
    }

    fun getPlayer(id: PlayerId) = playerStorage.get(id)
}
