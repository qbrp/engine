package org.lain.engine.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.AtlasSourceTypeRegistry
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.network.DisconnectionInfo
import org.lain.engine.AuthPacket
import org.lain.engine.EngineMinecraftServerDependencies
import org.lain.engine.SERVERBOUND_AUTH_ENDPOINT
import org.lain.engine.client.mc.MinecraftChat
import org.lain.engine.client.mc.MinecraftAudioManager
import org.lain.engine.client.mc.MinecraftCamera
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.ClientMinecraftNetwork
import org.lain.engine.client.resources.EngineAtlasSource.Companion.ID
import org.lain.engine.client.resources.EngineAtlasSource.Companion.TYPE
import org.lain.engine.client.mc.KeybindManager
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.mc.MinecraftFontRenderer
import org.lain.engine.client.mc.MinecraftPainter
import org.lain.engine.client.mc.getClientEntity
import org.lain.engine.client.mc.renderChatBubbles
import org.lain.engine.client.mixin.DrawContextAccessor
import org.lain.engine.client.render.Window
import org.lain.engine.client.server.ClientSingleplayerTransport
import org.lain.engine.client.server.IntegratedEngineMinecraftServer
import org.lain.engine.client.server.ServerSingleplayerTransport
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.util.setupOptionsConfig
import org.lain.engine.mc.updatePlayerMinecraftSystems
import org.lain.engine.transport.network.DisconnectText
import org.lain.engine.serverMinecraftPlayerInstance
import org.lain.engine.util.EngineId
import org.lain.engine.util.Injector
import org.lain.engine.util.MinecraftUsername
import org.lain.engine.util.engineId
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.parseMiniMessage
import org.lain.engine.util.registerMinecraftServer
import java.util.Optional

class MinecraftEngineClient : ClientModInitializer {
    private val client = MinecraftClient
    private val fabricLoader = FabricLoader.getInstance()
    private val playerTable by injectEntityTable()

    private val window = Window()
    private val fontRenderer = MinecraftFontRenderer()
    private val audioManager = MinecraftAudioManager(client)
    private val camera = MinecraftCamera(client)

    private val engineClient = EngineClient(
        window,
        fontRenderer,
        camera,
        MinecraftChat,
        audioManager,
        onPlayerInstantiate =  {
            val entity = client.world!!.players.first { entity -> entity.uuid == it.id.value }
            playerTable.setPlayer(entity, it)
        },
        onPlayerDestroy = {
            playerTable.removePlayer(it)
        }
    )
        .also { Injector.register(it) }

    private var server: IntegratedEngineMinecraftServer? = null
    private val renderer
        get() = engineClient.renderer

    private val config = setupOptionsConfig()
    private val configScreen = config.generateScreen(null)
        ?.also { Injector.register(it) }

    override fun onInitializeClient() {
        KeybindManager
        AtlasSourceTypeRegistry.register(ID, TYPE)

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
                authorize(entity)
            }

            engineClient.handler.run()
        }

        ClientLifecycleEvents.CLIENT_STARTED.register { onClientStarted() }

        ClientLoginConnectionEvents.DISCONNECT.register { handler, client -> onDisconnect() }

        ClientPlayConnectionEvents.DISCONNECT.register { handler, client -> onDisconnect() }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            try {
                window.handleResize()
                engineClient.tick()
                KeybindManager.tick(engineClient)
            } catch (e: Throwable) {
                e.printStackTrace()
                client.networkHandler?.let {
                    it.onDisconnected(
                        DisconnectionInfo(
                            DisconnectText(e.message ?: "Unknown error").parseMiniMessage(),
                            Optional.empty(),
                            Optional.empty()
                        )
                    )
                    onDisconnect()
                }
            }
            engineClient.gameSession?.let { session ->
                session.playerStorage.forEach { player ->
                    val entity = playerTable.getEntity(player) ?: return@forEach
                    val world = session.world
                    updatePlayerMinecraftSystems(player, entity, world)
                }
                renderer.tick()
            }
        }

        HudLayerRegistrationCallback.EVENT.register { wrapper ->
            wrapper.attachLayerAfter(
                IdentifiedLayer.SUBTITLES,
                IdentifiedLayer.of(
                    EngineId("ui")
                ) { context, counter ->
                    val deltaTick = counter.getTickDelta(true)
                    val vertexConsumers = (context as DrawContextAccessor).`engine$getVertexConsumerProvider`()
                    val painter = MinecraftPainter(
                        deltaTick,
                        context.matrices,
                        vertexConsumers,
                        fontRenderer
                    )
                    context.matrices.push()
                    renderer.renderScreen(painter, !client.gameRenderer.camera.isThirdPerson)
                    context.matrices.pop()
                }
            )
        }

        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            val gameSession = engineClient.gameSession ?: return@register
            val tickDelta = context.tickCounter().getTickDelta(true)
            val matrices = context.matrixStack()!!
            val vertexConsumers = context.consumers()!!
            val cameraPos = context.camera().pos
            matrices.push()
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)
            val chatBubbleManager = gameSession.chatBubbleManager
            gameSession.playerStorage.forEach {
                val entity = playerTable.getClientEntity(it) ?: return@forEach
                val chatBubble = chatBubbleManager.getChatBubble(it) ?: return@forEach
                chatBubbleManager.update(chatBubble, it, tickDelta)
                renderChatBubbles(entity, tickDelta, chatBubble, engineClient.options.chatBubbleScale.get(), matrices, vertexConsumers)
            }
            matrices.pop()
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
        if (client.gameProfile.name == "denterest1") {
            audioManager.playPigScreamSound()
        }
    }

    private fun onDisconnect() {
        engineClient.leaveGameSession()
        MinecraftChat.clearChatData()

        val entity = client.player ?: return
        playerTable.removePlayer(entity)
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