package org.lain.engine.client

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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.world.chunk.Chunk
import org.lain.engine.*
import org.lain.engine.client.mc.*
import org.lain.engine.client.mc.ClientMixinAccess.renderChatBubbles
import org.lain.engine.client.mc.render.*
import org.lain.engine.client.mixin.MinecraftClientAccessor
import org.lain.engine.client.render.Window
import org.lain.engine.client.server.ClientSingleplayerTransport
import org.lain.engine.client.server.IntegratedEngineMinecraftServer
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.ENGINE_ITEM_REFERENCE_COMPONENT
import org.lain.engine.mc.ServerMixinAccess
import org.lain.engine.mc.updatePlayerMinecraftSystems
import org.lain.engine.player.Interaction
import org.lain.engine.player.OrientationTranslation
import org.lain.engine.player.setInteraction
import org.lain.engine.util.*
import org.lain.engine.util.math.randomInteger
import org.lain.engine.world.*
import org.slf4j.LoggerFactory

class MinecraftEngineClient : ClientModInitializer {
    private val client = MinecraftClient
    private val fabricLoader = FabricLoader.getInstance()
    private val entityTable by injectEntityTable()
    private val clientPlayerTable by lazy { entityTable.client }
    private var chunks = mutableListOf<Chunk>()

    private val window = Window(this)
    private val fontRenderer = MinecraftFontRenderer()
    private val audioManager = MinecraftAudioManager(client)
    private val camera = MinecraftCamera(client)
    val uiRenderPipeline = EngineUiRenderPipeline(client, fontRenderer)

    private val decalsStorage: ChunkDecalsStorage = ChunkDecalsStorage()
    private val eventBus = MinecraftEngineClientEventBus(client, clientPlayerTable, decalsStorage, chunks)
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
        registerEngineItemGroupEvent()

        Injector.register(keybindManager)

        ServerMixinAccess.blockRemovedCallback = { chunk, blockPos ->
            client.execute { decalsStorage.update(chunk, blockPos) }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, env ->
            dispatcher.register(
                ClientCommandManager.literal("reloadclientenginecontents")
                    .executes { ctx ->
                        updateEngineItemGroupEntries()
                        ctx.source.sendFeedback(Text.of("Контен скомпилирован"))
                        1
                    }
            )
        }

        ClientPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val entity = client.player!!

            if (engineClient.gameSessionActive) {
                connectionLogger.warn("Сброс активной игровой сессии. Это баг, который не должен возникать в обычных условиях")
                onDisconnect()
            }

            if (client.isInSingleplayer) {
                val server = server ?: throw RuntimeException("Server not started")
                val engine = server.engine
                val player = serverMinecraftPlayerInstance(server, entity, entity.engineId)
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    prepareServerMinecraftPlayer(server, entity, player)
                    engine.execute {
                        engine.playerService.instantiate(player)
                    }
                }
            } else {
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

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player

            ClientMixinAccess.tick()
            window.handleResize()
            try {
                if (!client.isInSingleplayer && readyToAuthorize && player != null && !inAuthorization) {
                    authorize(player)
                    inAuthorization = true
                }

                val skippedPlayers = mutableListOf<PlayerEntity>()

                engineClient.gameSession?.let { session ->
                    val mcWorld = client.world
                    session.admin = player?.permissionLevel == 4
                    mcWorld?.players?.forEach { entity ->
                        val player = clientPlayerTable.getPlayer(entity) ?: run {
                            skippedPlayers += entity
                            return@forEach
                        }
                        val world = session.world
                        val itemStacks = (entity.inventory + entity.currentScreenHandler.cursorStack).toSet()
                        val items = itemStacks.mapNotNull { itemStack ->
                            val reference = itemStack.get(ENGINE_ITEM_REFERENCE_COMPONENT) ?: return@mapNotNull null
                            val item = reference.getClientItem() ?: return@mapNotNull null
                            item to itemStack
                        }.toSet()

                        player.apply<OrientationTranslation> {
                            if (yaw != 0f || pitch != 0f) {
                                camera.impulse(-yaw, -pitch)
                            }
                        }

                        updatePlayerMinecraftSystems(player, items, entity, world)
                        if (engineClient.ticks % 20L == 0L) {
                            updateRandomEngineItemGroupIcon()
                        }
                    }
                    renderer.tick()
                    if (mcWorld != null) {
                        updateBulletsVisual(session.world, mcWorld, session.mainPlayer, decalsStorage)
                    }
                }

                if (skippedPlayers.isNotEmpty()) {
                    connectionLogger.warn("Состояние Minecraft не было обновлено для игроков: {}", skippedPlayers.joinToString { it.stringifiedName })
                }

                engineClient.tick()
                keybindManager.tick(engineClient)
            } catch (e: Throwable) {
                connectionLogger.error("При тике Engine возникла ошибка: ", e)
                client.networkHandler?.connection?.disconnect(DisconnectText(e.message ?: "Unknown error")) ?: run {
                    connectionLogger.warn("Игрок отключен от несуществующего сервера")
                }
            }
        }

        WorldRenderEvents.END_MAIN.register { context ->
            val gameRenderer = context.gameRenderer()
            val camera = gameRenderer.camera
            val cameraPos = camera.pos
            val matrices = context.matrices()
            val queue = context.commandQueue()

            val gameSession = engineClient.gameSession
            val acousticDebugVolumes = gameSession?.acousticDebugVolumes
            val playerBlockPos = client.player?.blockPos ?: return@register
            if (engineClient.developerMode && engineClient.acousticDebug && gameSession != null && acousticDebugVolumes?.isNotEmpty() == true) {
                renderAcousticDebugLabels(
                    eventBus.acousticDebugVolumesBlockPosCache,
                    listOf(playerBlockPos, playerBlockPos.add(0, 1, 0)),
                    gameSession.vocalRegulator.volume.base,
                    gameSession.vocalRegulator.volume.max,
                    queue,
                    matrices,
                    gameRenderer.entityRenderStates.cameraRenderState
                )
            }

            val vertexConsumers = context.consumers()
            if (vertexConsumers !is VertexConsumerProvider.Immediate) return@register
            renderChatBubbles(matrices, camera, vertexConsumers, cameraPos.x, cameraPos.y, cameraPos.z)
        }

        WorldRenderEvents.BEFORE_ENTITIES.register { context ->
            val matrices = context.matrices()
            val queue = context.commandQueue()
            val camera = context.gameRenderer().camera
            val images = decalsStorage.getBlockImages()
            matrices.push()
            matrices.translate(camera.pos.negate())
            for ((pos, image) in images) {
                val (chunkPos, blockPos) = pos.first to pos.second
                for (layer in image.layers) {
                    renderBlockDecals(layer, blockPos, matrices, queue)
                }
            }
            matrices.pop()
        }

        var debugDecalsVersion = 0
        UseBlockCallback.EVENT.register { entity, world, hand, result ->
            if (world.isClient && engineClient.developerMode && isControlDown()) {
                val pos = result.blockPos
                val chunk = world.getChunk(pos)
                val decals = List(10) {
                    Decal(
                        randomInteger(16),
                        randomInteger(16),
                        DecalContents.Chip(1)
                    )
                }
                decalsStorage.modify(
                    chunk,
                    pos,
                    BlockDecals(
                        debugDecalsVersion++,
                        listOf(
                            DecalsLayer(
                                Direction.entries.associateWith { decals }
                            )
                        )
                    )
                )
            }
            ActionResult.PASS
        }

        ClientChunkEvents.CHUNK_UNLOAD.register { world, chunk ->
            chunks -= chunk
            decalsStorage.unload(chunk.pos)
        }

        ClientChunkEvents.CHUNK_LOAD.register { world, chunk ->
            chunks += chunk
            decalsStorage.survey(chunk)
        }

        UseItemCallback.EVENT.register { player, world, hand ->
            if (world.isClient) {
                if (player.isMainPlayer) {
                    engineClient.gameSession?.mainPlayer?.setInteraction(Interaction.RightClick)
                }
            }
            ActionResult.PASS
        }

        HudElementRegistry.addLast(
            EngineId("ui")
        ) { context, tickCounter ->
            val deltaTick = tickCounter.fixedDeltaTicks
            val painter = MinecraftPainter(
                deltaTick,
                context,
                fontRenderer
            )
            context.matrices.pushMatrix()
            renderer.isFirstPerson = !client.gameRenderer.camera.isThirdPerson
            renderer.renderScreen(painter)
            val window = MinecraftClient.window
            val mouse = MinecraftClient.mouse
            uiRenderPipeline.render(
                context,
                deltaTick,
                mouse.getScaledX(window).toFloat(),
                mouse.getScaledY(window).toFloat()
            )
            context.matrices.popMatrix()
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
        decalsStorage.clear()

        if (engineClient.gameSessionActive) {
            engineClient.leaveGameSession()
            MinecraftChat.clearChatData()
        }
        readyToAuthorize = false
        inAuthorization = false
        connectionLogger.info("Игрок отключен от сервера Engine")
    }

    private fun authorize(player: ClientPlayerEntity) {
        SERVERBOUND_AUTH_ENDPOINT
            .sendC2SPacket(
                AuthPacket(
                    MinecraftUsername(player),
                    fabricLoader.allMods.map { it.metadata.id }
                )
            )
    }
}