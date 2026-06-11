package org.lain.engine.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.inventory.BookEditScreen
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.server.network.Filterable
import net.minecraft.util.profiling.Profiler
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.WritableBookContent
import net.minecraft.world.level.Level
import org.lain.cyberia.ecs.apply
import org.lain.cyberia.ecs.handle
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.remove
import org.lain.engine.AuthPacket
import org.lain.engine.Constants.ENGINE_MOD_VERSION
import org.lain.engine.SERVERBOUND_AUTH_ENDPOINT
import org.lain.engine.client.mc.*
import org.lain.engine.client.mc.chat.MinecraftChat
import org.lain.engine.client.mc.compat.LightSystem
import org.lain.engine.client.mc.compat.injectDynamicLightsContext
import org.lain.engine.client.mc.sound.MinecraftAudioManager
import org.lain.engine.client.mixin.MinecraftClientAccessor
import org.lain.engine.client.render.Window
import org.lain.engine.client.render.legacy.EngineUiRenderPipeline
import org.lain.engine.client.render.ui.InteractionSelectionScreen
import org.lain.engine.client.render.ui.initializeGraphene
import org.lain.engine.client.render.ui.registerHudRenderEvent
import org.lain.engine.client.render.world.DecalSystem
import org.lain.engine.client.render.world.EquipmentFeatureRenderer
import org.lain.engine.client.render.world.HeadEquipmentFeatureRenderer
import org.lain.engine.client.render.world.registerWorldRenderEvents
import org.lain.engine.client.server.IntegratedEngineMinecraftServer
import org.lain.engine.client.server.registerEngineIntegratedServerEvent
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.registerComponentsClient
import org.lain.engine.item.BookOpen
import org.lain.engine.mc.*
import org.lain.engine.player.*
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.server.EngineServer
import org.lain.engine.serverMinecraftPlayerLoadSettings
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.Injector
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectValue
import org.lain.engine.world.ImmutableVoxelPos
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.math.sqrt

class EngineMinecraftClient : ClientModInitializer {
    private val client = MinecraftClient
    private val fabricLoader = FabricLoader.getInstance()
    private val entityTable by injectEntityTable()
    private val dynamicLights by injectDynamicLightsContext()
    private val clientPlayerTable by lazy { entityTable.client }

    private val window = Window(this)
    private val audioManager = MinecraftAudioManager(client)
    private val camera = MinecraftCamera(client)
    val uiRenderPipeline = EngineUiRenderPipeline(client)

    private lateinit var lightSystem: LightSystem
    private val decalsStorage: DecalSystem = DecalSystem()
    private val eventBus = MinecraftEngineClientEventBus(client, entityTable, decalsStorage)
    private var config: EngineYamlConfig = EngineYamlConfig()
    private val engineClient = EngineClient(
        window,
        camera,
        MinecraftChat,
        audioManager,
        uiRenderPipeline,
        eventBus
    )
        .also { Injector.register(it) }

    private lateinit var keybindManager: KeybindManager
    private val renderer
        get() = engineClient.renderer

    private val connectionLogger = LoggerFactory.getLogger("Engine Connection")
    private var inAuthorization = false
    var readyToAuthorize = false
    var server: IntegratedEngineMinecraftServer? = null

    private fun isInWorld(world: Level): Boolean = client.level?.engine?.value == world.engine.value

    override fun onInitializeClient() {
        ComponentTypeRegistry.registerComponentsClient()
        engineClient.options = config
        keybindManager = KeybindManager(config = config.config)
        registerEngineItemGroupEvent(engineClient)
        registerDeveloperModeDecalsDebug(decalsStorage, engineClient)
        registerClientEngineCommands(engineClient)
        initializeGraphene()

        Injector.register(keybindManager)
        Injector.register(this)

        ServerMixinAccess.blockPlacedCallback = callback@{ player, blockPos, blockState, world ->
            val gameSession = engineClient.gameSession
            if (!world.isClientSide || !isInWorld(world) || gameSession == null) return@callback
            client.execute {
                val enginePlayer = player?.let { clientPlayerTable.getPlayer(it) }
                gameSession.callbacks.executePlaceVoxelCallback(enginePlayer, gameSession.world, blockPos.voxelPos(), blockState)
            }
        }

        ServerMixinAccess.blockInteractionCallback = callback@ { _, world, blockPos ->
            val gameSession = engineClient.gameSession
            if (!world.isClientSide || !isInWorld(world) || gameSession == null) return@callback false
            val voxel = gameSession.world.chunkStorage.getDynamicVoxel(blockPos.voxelPos()) ?: return@callback false
            with(gameSession.world) { voxel.hasComponent(CoreScriptComponents.USE_RESTRICTION) }
        }

        ServerMixinAccess.blockRemovedCallback = callback@{ chunk, blockPos ->
            if (!chunk.level.isClientSide || !isInWorld(chunk.level)) return@callback
            client.execute {
                val pos = ImmutableVoxelPos(blockPos.voxelPos())
                engineClient.gameSession?.world?.chunkStorage?.removeVoxel(pos)
                decalsStorage.unloadTexture(pos)
            }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            if (engineClient.gameSessionActive) {
                connectionLogger.warn("Сброс активной игровой сессии. Это баг, который не должен возникать в обычных условиях")
                onDisconnect()
            }

            if (!client.isSingleplayer) {
                Injector.register<ClientTransportContext>(ClientMinecraftNetwork())
                Injector.register<RaycastProvider>(MinecraftRaycastProvider(injectValue()))
            }

            engineClient.handler.run()
            MinecraftChat.registerEndpoints()

            inAuthorization = false
            readyToAuthorize = true
        }

        ServerPlayerEvents.JOIN.register { player ->

        }

        ClientLifecycleEvents.CLIENT_STARTED.register { onClientStarted() }

        ClientTickEvents.START_CLIENT_TICK.register { keybindManager.tick(engineClient) }

        ClientTickEvents.END_CLIENT_TICK.register { _ -> tickClient() }

        ClientChunkEvents.CHUNK_UNLOAD.register { _, chunk ->
            decalsStorage.unloadTextures(chunk.pos.engineChunkPos())
        }

        registerEngineIntegratedServerEvent(engineClient)

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register { _, renderer, helper, _ ->
            if (renderer is AvatarRenderer) {
                helper.register(EquipmentFeatureRenderer(renderer))
                helper.register(HeadEquipmentFeatureRenderer(renderer))
            }
        }
    }

    private fun tickClient() {
        val profiler = Profiler.get()
        profiler.push("engineClientTick")

        val levelPlayers = client.level?.players() ?: emptyList()
        val mainPlayerEntity = levelPlayers.find { it.gameProfile == client.gameProfile } ?: client.player

        ClientMixinAccess.tick()
        window.handleResize()

        try {
            if (readyToAuthorize && mainPlayerEntity != null && !inAuthorization) {
                val developerMode = DeveloperModeStatus(engineClient.developerMode, engineClient.acousticDebug)
                if (client.isSingleplayer) {
                    val engine = server?.engine ?: throw RuntimeException("Server not started")
                    authorizeSingleplayer(engine, mainPlayerEntity, developerMode)
                } else {
                    authorizeMultiplayer()
                }
                inAuthorization = true
            }

            val gameSession = engineClient.gameSession
            val syncedLevelPlayerEntities = mutableMapOf<Player, EnginePlayer>()
            val skippedPlayers = mutableListOf<Pair<Player, Double>>()
            if (gameSession != null && mainPlayerEntity != null) {
                val synchronizationRadiusSqr = gameSession.synchronizationRadius * gameSession.synchronizationRadius
                val mainPlayerPos = mainPlayerEntity.position()
                syncedLevelPlayerEntities.putAll(
                    levelPlayers
                        .mapNotNull { entity ->
                            clientPlayerTable.getPlayer(entity)?.let { player -> entity to player }
                        }
                        .toMap()
                )
                skippedPlayers.addAll(
                    levelPlayers
                        .filter { it !in syncedLevelPlayerEntities }
                        .mapNotNull { player ->
                            val distance = player.position().distanceToSqr(mainPlayerPos)
                            player.takeIf { distance < synchronizationRadiusSqr }?.let { it to sqrt(distance) }
                        }
                )
            }

            preEngineTick(syncedLevelPlayerEntities)
            engineClient.tick()
            postEngineTick(syncedLevelPlayerEntities)

            if (skippedPlayers.isNotEmpty()) {
                connectionLogger.warn(
                    "Состояние Minecraft не было обновлено для игроков: {}",
                    skippedPlayers.joinToString { (player, distance) -> "${player.plainTextName} (до игрока: ${"%d".format(distance.toInt())})" }
                )
            }

        } catch (e: Throwable) {
            when (e) {
                is PlayerTickException -> e.log(connectionLogger)
                else -> connectionLogger.error("При тике Engine возникла ошибка: ", e)
            }

            disconnectWithReason(DisconnectText(e.message ?: "Неизвестная ошибка"))
            onDisconnect()
        }
        profiler.pop()
    }

    private fun preEngineTick(players: Map<Player, EnginePlayer>) {
        val mainPlayerEntity = client.player
        val gameSession = engineClient.gameSession ?: return
        val world = gameSession.world

        gameSession.mainPlayer.apply<OrientationTranslation> {
            if (yaw != 0f || pitch != 0f) {
                camera.impulse(-yaw, -pitch)
            }
        }

        world.prepareItemMinecraftSystem()
        players.forEach { (entity, player) ->
            try {
                val itemStacks = (entity.visibleInventoryItems + entity.carriedItem).toSet()
                val items = itemStacks.mapNotNull { itemStack ->
                    val item = itemStack.get(ENGINE_ITEM_REFERENCE_COMPONENT)?.getClientItem() ?: return@mapNotNull null
                    EngineItemStack(item, itemStack)
                }.toSet()

                updatePlayerMinecraftSystems(player, items, entity, world, gameSession.itemStorage)
                updatePlayerOwnedItems(world, player)
            } catch (e: Exception) {
                throw PlayerTickException(player, e)
            }
        }

        if (engineClient.ticks % 20L == 0L) {
            updateRandomEngineItemGroupIcon()
        }
    }

    private fun postEngineTick(players: Map<Player, EnginePlayer>) {
        val gameSession = engineClient.gameSession ?: return
        val minecraftWorld = client.level ?: return
        val world = gameSession.world
        renderer.tick()

        players.forEach { (entity, player) ->
            player.remove<BookOpen>()?.let {
                if (player == gameSession.mainPlayer) {
                    client.setScreen(
                        BookEditScreen(
                            entity,
                            entity.mainHandItem,
                            InteractionHand.MAIN_HAND,
                            WritableBookContent(
                                it.writeable.contents.map { Filterable(it, Optional.empty()) },
                            )
                        )
                    )
                }
            }
        }

        gameSession.mainPlayer.handle<InteractionComponent> {
            val selection = selection
            if (selection != null && client.screen !is InteractionSelectionScreen) {
                client.setScreen(InteractionSelectionScreen(gameSession, selection, keybindManager))
            }
        }

        updateBulletsVisual(gameSession.world, minecraftWorld)
        lightSystem.update(gameSession)
        decalsStorage.update(gameSession.world)
        gameSession.world.clearEvents()
        audioManager.tick(gameSession)
    }

    private fun onClientStarted() {
        lightSystem = LightSystem(dynamicLights)

        if (listOf("remii", "denterest").contains(client.gameProfile.name)) {
            audioManager.playPigScreamSound()
        }
        decalsStorage.textureManager = client.textureManager
        engineClient.thread = (client as MinecraftClientAccessor).`engine$getThread`()
        registerWorldRenderEvents(client, engineClient, eventBus, decalsStorage, entityTable)
        registerHudRenderEvent(client, engineClient, renderer, uiRenderPipeline)
    }

    fun onDisconnect() {
        if (engineClient.gameSession == null) return
        uiRenderPipeline.invalidate()
        entityTable.client.invalidate()
        decalsStorage.unload()
        lightSystem.invalidate()

        if (engineClient.gameSessionActive) {
            engineClient.leaveGameSession()
        }
        MinecraftChat.clearChatData()
        readyToAuthorize = false
        inAuthorization = false
        connectionLogger.info("Игрок отключен от сервера Engine")
    }

    private fun authorizeSingleplayer(engine: EngineServer, entity: AbstractClientPlayer, developerStatus: DeveloperModeStatus) {
        val settings = engine.serverMinecraftPlayerLoadSettings(entity, entity.engineId, developerStatus, listOf())
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            engine.playerLoader.loadPreparing(
                settings = settings,
                exceptionHandler = { disconnectWithReason(DisconnectText(it)) }
            )
        }
    }

    private fun authorizeMultiplayer() {
        SERVERBOUND_AUTH_ENDPOINT
            .sendC2SPacket(
                AuthPacket(
                    fabricLoader.allMods.map { it.metadata.id },
                    ENGINE_MOD_VERSION
                )
            )
    }

    private fun disconnectWithReason(text: Text) {
        client.connection?.connection?.disconnect(text) ?: run {
            connectionLogger.warn("Игрок отключен от несуществующего сервера")
        }
    }
}