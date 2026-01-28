package org.lain.engine.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.entity.player.PlayerEntity
import org.lain.engine.AuthPacket
import org.lain.engine.EngineMinecraftServerDependencies
import org.lain.engine.SERVERBOUND_AUTH_ENDPOINT
import org.lain.engine.client.mc.*
import org.lain.engine.client.mc.ClientMixinAccess.renderChatBubbles
import org.lain.engine.client.mc.render.EngineUiRenderPipeline
import org.lain.engine.client.mc.render.MinecraftFontRenderer
import org.lain.engine.client.mc.render.MinecraftPainter
import org.lain.engine.client.render.Window
import org.lain.engine.client.server.ClientSingleplayerTransport
import org.lain.engine.client.server.IntegratedEngineMinecraftServer
import org.lain.engine.client.server.ServerSingleplayerTransport
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.mc.DisconnectText
import org.lain.engine.mc.updatePlayerMinecraftSystems
import org.lain.engine.serverMinecraftPlayerInstance
import org.lain.engine.util.*
import org.slf4j.LoggerFactory

class MinecraftEngineClient : ClientModInitializer {
    private val client = MinecraftClient
    private val fabricLoader = FabricLoader.getInstance()
    private val entityTable by injectEntityTable()
    private val clientPlayerTable by lazy { entityTable.client }

    private val window = Window(this)
    private val fontRenderer = MinecraftFontRenderer()
    private val audioManager = MinecraftAudioManager(client)
    private val camera = MinecraftCamera(client)
    val uiRenderPipeline = EngineUiRenderPipeline(client, fontRenderer)

    private val eventBus = MinecraftEngineClientEventBus(client, clientPlayerTable)
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
    private var readyToAuthorize = false
    private var inAuthorization = false

    override fun onInitializeClient() {
        engineClient.options = config
        keybindManager = KeybindManager()
        Injector.register(keybindManager)
        ClientPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val entity = client.player!!

            if (client.isInSingleplayer) {
                val server = server ?: throw RuntimeException("Server not started")
                val engine = server.engine
                engine.playerService.instantiate(
                    serverMinecraftPlayerInstance(
                        engine,
                        entity,
                        entity.engineId
                    )
                )
            } else {
                Injector.register<ClientTransportContext>(ClientMinecraftNetwork())
                readyToAuthorize = true
            }

            engineClient.handler.run()
        }

        ClientLifecycleEvents.CLIENT_STARTED.register { onClientStarted() }

        ClientLoginConnectionEvents.DISCONNECT.register { handler, client -> onDisconnect() }

        ClientPlayConnectionEvents.DISCONNECT.register { handler, client -> onDisconnect() }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player

            ClientMixinAccess.tick()
            window.handleResize()
            try {
                if (readyToAuthorize && player != null && !inAuthorization) {
                    authorize(player)
                    inAuthorization = true
                }

                val skippedPlayers = mutableListOf<PlayerEntity>()

                engineClient.gameSession?.let { session ->
                    client.world?.players?.forEach { entity ->
                        val player = clientPlayerTable.getPlayer(entity) ?: run {
                            skippedPlayers += entity
                            return@forEach
                        }
                        val world = session.world
                        updatePlayerMinecraftSystems(player, entity, world)
                    }
                    renderer.tick()
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
            val camera = context.gameRenderer().camera
            val cameraPos = camera.pos
            val matrices = context.matrices()
            val vertexConsumers = context.consumers()
            if (vertexConsumers !is VertexConsumerProvider.Immediate) return@register
            renderChatBubbles(matrices, camera, vertexConsumers, cameraPos.x, cameraPos.y, cameraPos.z)
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
            val transport = ServerSingleplayerTransport(engineClient)
            val dependencies = EngineMinecraftServerDependencies(server)
            Injector.register<ClientTransportContext>(ClientSingleplayerTransport(engineClient))

            registerMinecraftServer(
                IntegratedEngineMinecraftServer(
                    dependencies,
                    transport
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
    }

    private fun onDisconnect() {
        uiRenderPipeline.invalidate()
        entityTable.client.invalidate()

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