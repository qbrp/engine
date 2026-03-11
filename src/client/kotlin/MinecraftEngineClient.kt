package org.lain.engine.client

import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screen.ingame.BookEditScreen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.entity.PlayerEntityRenderer
import net.minecraft.component.type.WritableBookContentComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.RawFilteredPair
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.profiler.Profilers
import net.minecraft.world.chunk.Chunk
import org.lain.engine.*
import org.lain.engine.client.mc.*
import org.lain.engine.client.mc.render.*
import org.lain.engine.client.mc.render.world.ChunkDecalsStorage
import org.lain.engine.client.mc.render.world.EquipmentFeatureRenderer
import org.lain.engine.client.mc.render.world.HeadEquipmentFeatureRenderer
import org.lain.engine.client.mc.render.world.registerWorldRenderEvents
import org.lain.engine.client.mixin.MinecraftClientAccessor
import org.lain.engine.client.render.WARNING
import org.lain.engine.client.render.Window
import org.lain.engine.client.resources.OutfitTag
import org.lain.engine.client.server.ClientSingleplayerTransport
import org.lain.engine.client.server.IntegratedEngineMinecraftServer
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.client.util.PlayerTickException
import org.lain.engine.item.OpenBookTag
import org.lain.engine.item.Writable
import org.lain.engine.mc.*
import org.lain.engine.player.*
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.ReloadContentsRequestPacket
import org.lain.engine.transport.packet.SERVERBOUND_RELOAD_CONTENTS_REQUEST_ENDPOINT
import org.lain.engine.util.Injector
import org.lain.engine.util.WARNING_COLOR
import org.lain.engine.util.component.apply
import org.lain.engine.util.component.get
import org.lain.engine.util.component.handle
import org.lain.engine.util.component.remove
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.registerMinecraftServer
import org.lain.engine.world.ImmutableVoxelPos
import org.slf4j.LoggerFactory
import java.util.*

class MinecraftEngineClient : ClientModInitializer {
    private val client = MinecraftClient
    private val fabricLoader = FabricLoader.getInstance()
    private val entityTable by injectEntityTable()
    private val dynamicLights by injectDynamicLightsContext()
    private val dynamicLightSources = mutableSetOf<LightSource>()
    private val dynamicLightBehaviours = mutableMapOf<LightSource, DynamicLightBehavior>()
    private val clientPlayerTable by lazy { entityTable.client }
    private var chunks = mutableListOf<Chunk>()

    private val window = Window(this)
    private val fontRenderer = MinecraftFontRenderer()
    private val audioManager = MinecraftAudioManager(client)
    private val camera = MinecraftCamera(client)
    val uiRenderPipeline = EngineUiRenderPipeline(client, fontRenderer)

    private val decalsStorage: ChunkDecalsStorage = ChunkDecalsStorage()
    private val eventBus = MinecraftEngineClientEventBus(client, clientPlayerTable, decalsStorage)
    private var config: EngineYamlConfig = EngineYamlConfig()
    private val engineClient = EngineClient(
        window,
        fontRenderer,
        camera,
        MinecraftChat,
        audioManager,
        uiRenderPipeline,
        eventBus
    )
        .also { Injector.register(it) }

    private lateinit var keybindManager: KeybindManager
    private var server: IntegratedEngineMinecraftServer? = null
    private val renderer
        get() = engineClient.renderer

    private val connectionLogger = LoggerFactory.getLogger("Engine Connection")
    private var inAuthorization = false
    var readyToAuthorize = false

    override fun onInitializeClient() {
        engineClient.options = config
        keybindManager = KeybindManager(config = config.config)
        registerEngineItemGroupEvent(engineClient)
        registerDeveloperModeDecalsDebug(decalsStorage, engineClient)
        registerWorldRenderEvents(client, engineClient, eventBus, decalsStorage)
        registerHudRenderEvent(client, engineClient, fontRenderer, renderer, uiRenderPipeline)
        OutfitTag.TYPE // lazy init

        Injector.register(keybindManager)

        ServerMixinAccess.blockRemovedCallback = { chunk, blockPos ->
            client.execute {
                val pos = ImmutableVoxelPos(blockPos.engine())
                engineClient.gameSession?.world?.chunkStorage?.removeVoxel(pos)
                decalsStorage.unloadTexture(pos)
            }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, env ->
            dispatcher.register(
                ClientCommandManager.literal("reloadenginecontents")
                    .executes { ctx ->
                        SERVERBOUND_RELOAD_CONTENTS_REQUEST_ENDPOINT.sendC2SPacket(ReloadContentsRequestPacket)

                        try {
                            require(engineClient.gameSession != null) { friendlyError("Вы не находитесь на сервере") }
                            engineClient.gameSession?.recompileContents()
                        } catch (e: Exception) {
                            engineClient.applyLittleNotification(
                                LittleNotification(
                                    "Ошибка компиляции клиента",
                                    "${e.message ?: "Неизвестная ошибка"}<newline>Проверьте консоль для более подробной информации.",
                                    WARNING_COLOR,
                                    WARNING,
                                    lifeTime = 240
                                )
                            )
                            e.printStackTrace()
                        }

                        ctx.source.sendFeedback(Text.of("Контен скомпилирован"))
                        1
                    }
            )
        }

        ClientPlayConnectionEvents.JOIN.register { handler, _, _ ->
            if (engineClient.gameSessionActive) {
                connectionLogger.warn("Сброс активной игровой сессии. Это баг, который не должен возникать в обычных условиях")
                onDisconnect()
            }

            if (!client.isInSingleplayer) {
                Injector.register<ClientTransportContext>(ClientMinecraftNetwork())
            }

            engineClient.handler.run()
            MinecraftChat.registerEndpoints()

            inAuthorization = false
            readyToAuthorize = true
        }

        ClientLifecycleEvents.CLIENT_STARTED.register { onClientStarted() }

        ClientLoginConnectionEvents.DISCONNECT.register { handler, client -> onDisconnect() }

        ClientPlayConnectionEvents.DISCONNECT.register { handler, client -> onDisconnect() }

        ClientTickEvents.START_CLIENT_TICK.register { keybindManager.tick(engineClient) }

        ClientTickEvents.END_CLIENT_TICK.register { client -> tickClient() }

        ClientChunkEvents.CHUNK_UNLOAD.register { world, chunk ->
            chunks -= chunk
            decalsStorage.unloadTextures(chunk.pos.engine())
        }

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dependencies = EngineMinecraftServerDependencies(server)
            Injector.register<ClientTransportContext>(ClientSingleplayerTransport(engineClient))

            registerMinecraftServer(
                IntegratedEngineMinecraftServer(
                    dependencies,
                    engineClient
                ).also { this.server = it }
            )
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            this.server = null
        }

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register { type, renderer, helper, context ->
            if (renderer is PlayerEntityRenderer) {
                helper.register(EquipmentFeatureRenderer(renderer))
                helper.register(HeadEquipmentFeatureRenderer(renderer))
            }
        }
    }

    private fun tickClient() {
        Profilers.get().push("engineClientTick")
        val entity = client.player
        val gameSession = engineClient.gameSession

        ClientMixinAccess.tick()
        window.handleResize()

        try {
            if (readyToAuthorize && entity != null && !inAuthorization) {
                authorize(entity)
                inAuthorization = true
            }
            val players = mutableMapOf<PlayerEntity, EnginePlayer>()
            val skippedPlayers = mutableListOf<PlayerEntity>()
            entity?.entityWorld?.players?.forEach { entity ->
                val player = clientPlayerTable.getPlayer(entity) ?: run {
                    skippedPlayers += entity
                    return@forEach
                }
                players[entity] = player
            }

            preEngineTick(players)
            engineClient.tick()
            postEngineTick(players)

            if (skippedPlayers.isNotEmpty()) {
                connectionLogger.warn("Состояние Minecraft не было обновлено для игроков: {}", skippedPlayers.joinToString { it.stringifiedName })
            }

        } catch (e: Throwable) {
            connectionLogger.error("При тике Engine возникла ошибка: ", e)

            val text = when (e) {
                is PlayerTickException -> "Ошибка при обновлении игрока ${e.player.username} (${e.player.id}): ${e.message}"
                else -> e.message ?: "Неизвестная ошибка"
            }

            client.networkHandler?.connection?.disconnect(DisconnectText(text)) ?: run {
                connectionLogger.warn("Игрок отключен от несуществующего сервера")
            }

            onDisconnect()
        }
        Profilers.get().pop()
    }

    private fun preEngineTick(players: Map<PlayerEntity, EnginePlayer>) {
        val mainPlayerEntity = client.player
        val gameSession = engineClient.gameSession ?: return
        val world = gameSession.world
        gameSession.admin = mainPlayerEntity?.permissionLevel == 4

        gameSession.mainPlayer.apply<OrientationTranslation> {
            if (yaw != 0f || pitch != 0f) {
                camera.impulse(-yaw, -pitch)
            }
        }

        players.forEach { (entity, player) ->
            val itemStacks = (entity.inventory + entity.currentScreenHandler.cursorStack).toSet()
            val items = itemStacks.mapNotNull { itemStack ->
                val item = itemStack.get(ENGINE_ITEM_REFERENCE_COMPONENT)?.getClientItem() ?: return@mapNotNull null
                item to itemStack
            }.toSet()

            removeHoldsByMarks(gameSession.itemStorage.getAll())
            updatePlayerMinecraftSystems(player, items, entity, world, gameSession.itemStorage)
        }

        if (engineClient.ticks % 20L == 0L) {
            updateRandomEngineItemGroupIcon()
        }
    }

    private fun postEngineTick(players: Map<PlayerEntity, EnginePlayer>) {
        val gameSession = engineClient.gameSession ?: return
        val minecraftWorld = client.world ?: return
        renderer.tick()

        players.forEach { (entity, player) ->
            player.remove<OpenBookTag>()?.let {
                if (player == gameSession.mainPlayer) {
                    val writable = player.handItem?.get<Writable>() ?: return@let
                    client.setScreen(
                        BookEditScreen(
                            entity,
                            entity.mainHandStack,
                            Hand.MAIN_HAND,
                            WritableBookContentComponent(
                                writable.contents.map { RawFilteredPair(it, Optional.empty()) },
                            )
                        )
                    )
                }
            }
        }

        gameSession.mainPlayer.handle<InteractionComponent> {
            val selection = selection
            if (selection != null && client.currentScreen !is InteractionSelectionScreen) {
                client.setScreen(InteractionSelectionScreen(gameSession, selection, keybindManager))
            }
        }

        updateBulletsVisual(gameSession.world, minecraftWorld)
        updateLights(gameSession, dynamicLights, entityTable, dynamicLightSources, dynamicLightBehaviours)
        decalsStorage.handleDecalsEvent(gameSession.world)
        gameSession.world.clearEvents()
    }

    private fun onClientStarted() {
        if (listOf("remii", "denterest").contains(client.gameProfile.name)) {
            audioManager.playPigScreamSound()
        }
        decalsStorage.textureManager = client.textureManager
        engineClient.thread = (client as MinecraftClientAccessor).`engine$getThread`()
    }

    private fun onDisconnect() = client.execute {
        uiRenderPipeline.invalidate()
        entityTable.client.invalidate()
        decalsStorage.unload()

        if (engineClient.gameSessionActive) {
            engineClient.leaveGameSession()
        }
        MinecraftChat.clearChatData()
        readyToAuthorize = false
        inAuthorization = false
        connectionLogger.info("Игрок отключен от сервера Engine")
    }

    private fun authorize(entity: ClientPlayerEntity) {
        val developerMode = DeveloperModeStatus(engineClient.developerMode, engineClient.acousticDebug)
        if (client.isInSingleplayer) {
            val server = server ?: throw RuntimeException("Server not started")
            val engine = server.engine
            val player = serverMinecraftPlayerInstance(server, entity, entity.engineId, developerMode)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                prepareServerMinecraftPlayer(server, entity, player)
                engine.execute {
                    engine.instantiatePlayer(player)
                }
            }
        } else {
        SERVERBOUND_AUTH_ENDPOINT
            .sendC2SPacket(
                AuthPacket(
                    MinecraftUsername(entity),
                    fabricLoader.allMods.map { it.metadata.id },
                    developerMode,
                    ENGINE_MOD_VERSION
                )
            )
        }
    }
}