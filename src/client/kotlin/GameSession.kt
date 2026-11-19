package org.lain.engine.client

import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.client.account.CharacterChange
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.chat.ChatBubbleList
import org.lain.engine.client.chat.ClientChatManager
import org.lain.engine.client.chat.PlayerVocalRegulator
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.control.*
import org.lain.engine.client.handler.*
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.render.*
import org.lain.engine.client.render.player.SkinSystem
import org.lain.engine.client.render.player.clearPlayerRenderState
import org.lain.engine.client.render.player.extractPlayerRenderState
import org.lain.engine.client.render.player.snapshotCharacterRenderState
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.client.render.ui.TransformationEdit
import org.lain.engine.client.script.ClientLuaScriptEngine
import org.lain.engine.client.script.EntityInspection
import org.lain.engine.client.script.compileScriptsOrThrow
import org.lain.engine.client.script.tickEntityRpcQueueSystem
import org.lain.engine.client.transport.ClientTransport
import org.lain.engine.client.util.ClientHintState
import org.lain.engine.client.util.handleHintEvents
import org.lain.engine.client.util.processWorldSounds
import org.lain.engine.data.PersistentId
import org.lain.engine.item.ItemStorage
import org.lain.engine.player.*
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.CharacterApplyEvent
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.exists
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.script.InventoryTab
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.compilation.CompilationFailedException
import org.lain.engine.server.ServerId
import org.lain.engine.transport.packet.*
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.Log
import org.lain.engine.util.WARNING_COLOR
import org.lain.engine.world.*

class GameSession(
    val isMultiPlayer: Boolean,
    val server: ServerId,
    setup: ClientboundSetupData,
    world: ClientboundWorldData,
    player: ServerPlayerData,
    var character: LocalCharacter?,
    transport: ClientTransport,
    private val sessionPackets: SessionPacketReceiver,
    val client: EngineClient,
    val luaContext: ClientLuaScriptEngine,
    build: Build,
    val joinType: GameSessionJoinFlow.JoinType,
    var serverPlayState: ServerPlayState,
    val hintState: ClientHintState = ClientHintState(),
) : EngineSimulation.SimulationTickExtension, EngineSimulation.Settings {
    private val systems: ClientPlatform.TickExtension = client.platform.tickExtension
    val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
    val playerStorage = PlayerStorage()
    val handler = ClientHandler(this, transport)
    var inventoryTab: InventoryTab = build.inventoryTab

    val simulation = EngineSimulation(
        isClient = true,
        this,
        this,
        playerStorage,
        namespacedStorage,
        luaContext,
        client.thread,
        setup.settings.useAdventureMode
    )
    val world = World(world.id, simulation)
    val renderScene = RenderScene(namespacedStorage)
    val itemStorage: ItemStorage = this.world.itemStorage
    val chatEventBus = client.chatEventBus

    var synchronizationRadius: Int = setup.settings.synchronizationRadius
    var playerDesynchronizationThreshold: Int = setup.settings.playerDesynchronizationThreshold
    val replicationController = ClientReplicationController(
        gameSession = this,
        initialReplicationState = player.replicationSnapshot,
    )

    var extendArm = false
        set(value) {
            handler.onArmStatusUpdate(value)
            mainPlayer.extendArm = value
            field = value
        }

    var acousticDebugVolumes = listOf<Pair<VoxelPos, Float>>()
    val movementManager = MovementManager(this)
    val chatBubbleList = ChatBubbleList(client.options)
    val chatManager = ClientChatManager(this, setup.settings.chat)

    val skinSystem = SkinSystem(client.lookTextureManager)

    val vocalRegulator = PlayerVocalRegulator(
        PlayerVolume(player.volume, player.maxVolume, player.baseVolume),
        this
    )
    val mainPlayer = mainClientPlayerInstance(
        player.id,
        this.world,
        player,
        DeveloperModeStatus(client.developerMode, client.acousticDebug),
    )
    val onlinePlayers = OnlinePlayers()

    val ticks
        get() = simulation.ticks

    val endTickTaskExecutor = TaskExecutor()
    val dataPrepareTaskExecutor = TaskExecutor()

    var inspectionMode: Boolean = false
        set(value) {
            client.showInspectionModeToggleNotification(value)
            field = value
        }

    val inspection = InspectionMode()
    var characterChange: CharacterChange? = null
        private set

    var entityInspections: MutableMap<PersistentId, EntityInspection> = mutableMapOf()
    var transformationEdit: TransformationEdit? = null
    var photoMode: PhotoMode? = null
    val guide = Guide(this)

    init {
        applyCompilation(build)
        simulation.loadWorld(this.world)

        instantiatePlayer(mainPlayer)

        client.platform.onMainPlayerInstantiated(client, this, mainPlayer)
        setup.playerList.players.forEach {
            val onlinePlayer = onlinePlayers.add(it)
            addRemotePlayer(onlinePlayer)
        }
        onlinePlayers.add(GeneralPlayerData.of(mainPlayer))

        chatManager.updateSettings(setup.settings.chat)

        if (setup.settings.spectateOnJoin) {
            client.showSpectatingNotification()
        }

        replicationController.seed(player.replicationSnapshot)

        player.character?.let {
            this.world.emitEvent(CharacterApplyEvent(it, mainPlayer.id))
        }
    }

    fun logInMainThread(loggerGetter: context(World) GameSession.(tick: Long) -> Log) {
        val tick = ticks
        endTickTaskExecutor.execute {
            with(world) { EngineLogger.log(loggerGetter(tick)) }
        }
    }

    fun updateServerSettings(settings: ClientboundServerSettings) {
        val defaultAttributes = settings.defaultAttributes
        vocalRegulator.volume.apply {
            max = defaultAttributes.maxVolume
            base = defaultAttributes.baseVolume
        }
        synchronizationRadius = settings.synchronizationRadius
        playerDesynchronizationThreshold = settings.playerDesynchronizationThreshold
        chatManager.updateSettings(settings.chat)
        simulation.useAdventureMode = settings.useAdventureMode
    }

    fun toggleInspectionMode() {
        inspectionMode = !inspectionMode
    }

    fun applyCompilation(build: Build) {
        simulation.applyCompilationResult(build)
        luaContext.setupClientGameSession(this)
        client.audioManager.invalidateCache()
        inventoryTab = build.inventoryTab
        client.platform.onCompiled(this, build.inventoryTab)
    }

    fun recompile() {
        try {
            client.moduleManager.composeModules(luaContext)
            applyCompilation(luaContext.compileScriptsOrThrow())
        } catch (e: CompilationFailedException) {
            val errors = e.report.errors
            if (errors.isNotEmpty()) {
                val line1 =
                    if (errors.size == 1) "Возникла 1 ошибка" else "Возникло ${errors.size} ошибок"
                client.showNotification(
                    LittleNotification(
                        "Сбой компиляции контента",
                        "$line1. Проверьте консоль для более подробной информации.",
                        WARNING_COLOR,
                        WARNING,
                        lifeTime = 240
                    )
                )
                e.log()
            }
        }
    }

    override fun World.tickDaraPreparation() = with(systems) {
        tickDataPrepareBeforeReplication()
        dataPrepareTaskExecutor.flush()
        tickDataPrepareAfterReplication()
        handler.tickInput()
    }

    override fun World.beforeInteractions() {
        replicationController.beginPrediction(
            mainPlayer.entity.requireComponent<PlayerInput>().tick
        )
    }

    override fun World.beforeEventCleanup() {
        replicationController.endPrediction()
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
        client.platform.getHitResultVoxelPos()?.let {
            updateInspectionMode(inspection, inspectionMode, it)
        }

        skinSystem.tick(this@afterOperations)
        client.renderer.tick(this@GameSession)
        tickDataApplySystem()
    }

    fun tick() {
        chatManager.tick()

        movementManager.stamina = mainPlayer.stamina
        if (mainPlayer.has<SpawnMark>()) {
            client.removeLittleNotification(SPECTATOR_NOTIFICATION)
        }

        simulation.tick()
        handler.postTick()
        endTickTaskExecutor.flush()

        world.extractPlayerRenderState(renderScene, client.lookTextureManager)
    }

    fun loadChunk(pos: EngineChunkPos, chunk: EngineChunk) {
        world.chunkStorage.setChunk(pos, chunk)
        client.platform.onChunkLoad(pos, chunk)
    }

    fun addRemotePlayer(player: OnlinePlayer): EnginePlayer {
        val player = remoteClientPlayerInstance(world, player)
        instantiatePlayer(player)
        return player
    }

    fun removeRemotePlayer(player: EnginePlayer) {
        if (!player.exists()) return
        chatManager.typingPlayers.stop(player.id)
        with(world) { player.entity.clearPlayerRenderState(renderScene) }
        simulation.preparePlayerDestroy(player)
        simulation.destroyPlayer(player)
        client.platform.onPlayerDestroy(client, player.id)
        skinSystem.removePlayerFromCache(player.id)
    }

    fun instantiatePlayer(player: EnginePlayer) = with(player.world) {
        player.prepareContainers(player.location)
        simulation.instantiatePlayer(player)
    }

    fun openCharacterSelectionMenu() {
        simulation.assertOnThread()
        if (characterChange != null) return

        val currentPlayerCharacter = run {
            CharacterSelection.CurrentCharacter(
                mainPlayer.get<AppliedCharacter>()?.character?.data ?: return@run null,
                character?.source ?: return@run null,
                mainPlayer.get<SelectedLook>()?.look ?: return@run null
            )
        }

        // сохраняем рендер состояние
        character?.let { character ->
            runCatching { snapshotCharacterRenderState() }
                .onFailure { e -> LOGGER.error("Failed to serialize playerId render state", e) }
                .onSuccess { renderState ->
                    character.renderState = renderState
                    serverPlayState = serverPlayState.update(character)
                }
        }

        val change = CharacterChange(this, currentPlayerCharacter)
        characterChange = change
        change.start().invokeOnCompletion {
            dataPrepareTaskExecutor.execute { characterChange = null }
        }
    }

    fun destroy() {
        sessionPackets.close()
        characterChange?.cancel()
        characterChange = null
        world.clearPlayerRenderState(renderScene)
        replicationController.close()
        client.renderer.invalidate()
        handler.close()
        client.lookTextureManager.close()
        guide.save()
    }

    fun getPlayer(id: PlayerId) = playerStorage.get(id)
}
