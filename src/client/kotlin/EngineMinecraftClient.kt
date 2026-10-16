package org.lain.engine.client

import foundry.imgui.api.ImGuiMC
import foundry.imgui.api.ImGuiMCEvents
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import org.lain.engine.client.handler.GameSessionJoinFlow
import org.lain.engine.client.handler.ReplicationException
import org.lain.engine.client.handler.disconnectText
import org.lain.engine.client.mc.*
import org.lain.engine.client.mc.chat.MinecraftChat
import org.lain.engine.client.mc.compat.LightSystem
import org.lain.engine.client.mc.compat.registerEngineLightComponents
import org.lain.engine.client.mc.compat.injectDynamicLightsContext
import org.lain.engine.client.mc.compat.registerLamdDynLightEvents
import org.lain.engine.client.mc.compat.tickGenderSystem
import org.lain.engine.client.mc.sound.MinecraftAudioManager
import org.lain.engine.client.mixin.MinecraftClientAccessor
import org.lain.engine.client.render.Window
import org.lain.engine.client.render.ui.MovingWallpapers
import org.lain.engine.client.render.ui.hud.registerHudRenderEvent
import org.lain.engine.client.render.world.DecalSystem
import org.lain.engine.client.render.world.EquipmentFeatureRenderer
import org.lain.engine.client.render.world.HeadEquipmentFeatureRenderer
import org.lain.engine.client.render.world.LabelTextRenderTypes
import org.lain.engine.client.render.world.registerWorldRenderEvents
import org.lain.engine.client.resources.registerEngineModelLoading
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.util.postErrorReport
import org.lain.engine.client.util.registerComponentsClient
import org.lain.engine.mc.*
import org.lain.engine.server.account.EngineHttpClient
import org.lain.engine.player.*
import org.lain.engine.util.Injector
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.registerAllClient
import org.lain.engine.mc.compat.isReplayViewer
import org.lain.engine.mc.ecs.MinecraftSystem
import org.lain.engine.mc.ecs.tickGiveItemSystem
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.script.compilation.CompilationFailedException
import org.slf4j.LoggerFactory

class EngineMinecraftClient : ClientModInitializer, ClientPlatform.TickExtension {
    val client = MinecraftClient
    private val dynamicLights by injectDynamicLightsContext()
    val fabricLoader = FabricLoader.getInstance()

    private val window = Window()
    private val audioManager = MinecraftAudioManager(client)
    internal val camera = MinecraftCamera(client)
    val decalSystem: DecalSystem = DecalSystem(this)
    val platform = MinecraftEngineClientPlatform(this, client, decalSystem)
    internal lateinit var lightSystem: LightSystem
    private var config: EngineYamlConfig = EngineYamlConfig()
    val itemGroup = ItemGroup()
    val engine = EngineClient(
        window,
        camera,
        MinecraftChat,
        audioManager,
        platform,
        EngineHttpClient()
    )
        .also { Injector.register(it) }

    private lateinit var keybindManager: KeybindManager
    private val renderer
        get() = engine.renderer

    private var inAuthorization = false
    var readyToAuthorize = false
    var server: IntegratedEngineMinecraftServer? = null
    val currentLevel
        get() = client.level

    override fun onInitializeClient() {
        LabelTextRenderTypes.registerShaders()
        ComponentTypeRegistry.registerComponentsClient()
        ComponentTypeRegistry.registerAllClient()
        engine.options = config
        engine.onOptionsUpdate()
        keybindManager = KeybindManager(config = config.config)
        itemGroup.register()
        ClientMixin.createResourceList(engine)
        registerEngineModelLoading()
        registerEngineLightComponents()
        registerDeveloperModeDecalsDebug(decalSystem, engine)
        registerClientEngineCommands(engine)

        Injector.register(keybindManager)
        Injector.register(this)

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            if (engine.gameSessionActive) {
                LOGGER.warn("Сброс активной игровой сессии. Это баг, который не должен возникать в обычных условиях")
                onDisconnect()
            }

            if (!client.isSingleplayer) {
                Injector.register<ClientTransportContext>(ClientMinecraftNetwork())
                Injector.register<RaycastProvider>(MinecraftRaycastProvider())
            }

            val serverStartFail = IntegratedEngineMinecraftServer.serverStartFail
            if (serverStartFail == null) {
                engine.handler.run()
                MinecraftChat.registerEndpoints(engine.handler)

                inAuthorization = false
                readyToAuthorize = true
            } else {
                platform.disconnect(
                    if (serverStartFail is CompilationFailedException) {
                        serverStartFail.log()
                        "${serverStartFail.message}.<newline>Проверьте консоль для подробной информации"
                    } else {
                        postErrorReport(serverStartFail, "serverStart")
                        serverStartFail.message ?: "Не удалось запустить сервер"
                    }
                )
            }
        }

        ImGuiMCEvents.INSTANCE.preRenderImGuiEvent {
            engine.imGuiManager.render()
        }

        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent {
            engine.imGuiManager.postRender()
        }

        ClientLifecycleEvents.CLIENT_STARTED.register { onClientStarted() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { MovingWallpapers.close() }

        ClientTickEvents.START_CLIENT_TICK.register { keybindManager.tick(engine) }

        ClientTickEvents.END_CLIENT_TICK.register { _ -> tickClient() }

        ClientChunkEvents.CHUNK_UNLOAD.register { _, chunk ->
            decalSystem.unloadTextures(chunk.pos.engineChunkPos())
        }

        IntegratedEngineMinecraftServer.registerEvent(this)

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register { _, renderer, helper, _ ->
            if (renderer is PlayerRenderer) {
                helper.register(EquipmentFeatureRenderer(renderer))
                helper.register(HeadEquipmentFeatureRenderer(renderer))
            }
        }
    }

    private fun tickClient() {
        val mainPlayerEntity = client.player
        val profiler = client.profiler
        profiler.push("engineClientTick")

        ClientMixin.tick()
        window.handleResize()

        try {
            if (readyToAuthorize && mainPlayerEntity != null && !inAuthorization) {
                if (client.isSingleplayer) {
                    val server = server?.engine ?: throw RuntimeException("Server not started")
                    engine.startJoinFlow(
                        GameSessionJoinFlow.JoinType.Singleplayer(
                            server,
                            mainPlayerEntity.isReplayViewer
                        )
                    )
                } else {
                    engine.startJoinFlow(GameSessionJoinFlow.JoinType.Multiplayer)
                }
                inAuthorization = true
            }

            if (engine.ticks % 20L == 0L) {
                itemGroup.updateIcon()
            }

            engine.tick()
            renderer.tick()

        } catch (e: Exception) {
            LOGGER.error("При тике Engine возникла ошибка: ", e)

            val errorString = StringBuilder()

            if (e is ReplicationException) {
                errorString.append("<bold>Ошибка применения кадра репликации</bold><newline>")
                errorString.append(
                    "Игровая сессия была прекращена из-за непредвиденного исключения. " +
                            "Свяжитесь с разработчиком и сообщите о возникшем сбое.<newline>"
                )
                appendExceptionString(errorString, 0, e)
            } else {
                errorString.append(e.message ?: "Неизвестная ошибка")
            }

            disconnectWithReason(DisconnectText(errorString.toString()))
            postErrorReport(e, "tickClient")
            onDisconnect()
        }
        profiler.pop()
    }

    private fun appendExceptionString(string: StringBuilder, indent: Int, exception: Throwable) {
        string.append("  ".repeat(indent))
        string.append("- " + (exception.message ?: exception.toString()))
        string.append("<newline>")
        exception.cause?.let {
            appendExceptionString(string, indent + 1, it)
        }
    }

    private fun onClientStarted() {
        lightSystem = LightSystem(dynamicLights)
        registerLamdDynLightEvents(dynamicLights)

        if (listOf("remii", "denterest").contains(client.gameProfile.name)) {
            audioManager.playPigScreamSound()
        }
        decalSystem.textureManager = client.textureManager
        engine.thread = (client as MinecraftClientAccessor).`engine$getThread`()
        registerWorldRenderEvents(client, engine, platform, decalSystem)
        registerHudRenderEvent(client, engine, renderer)
        MovingWallpapers.loadWallpapers(client)
    }

    fun onDisconnect() {
        IntegratedEngineMinecraftServer.serverStartFail = null
        engine.stopJoinFlow()
        if (engine.gameSession == null) return

        engine.leaveGameSession()
        decalSystem.unload()
        lightSystem.invalidate()

        MinecraftChat.clearChatData()
        MinecraftAccessRegistry.invalidate()
        itemGroup.invalidate()

        readyToAuthorize = false
        inAuthorization = false
        LOGGER.info("Игрок отключен от сервера Engine")
    }

    fun disconnectWithReason(text: Text) {
        client.connection?.connection?.disconnect(text) ?: run {
            LOGGER.warn("Игрок отключен от несуществующего сервера")
        }
    }

    override fun GameSession.tickDataPrepareSystem() {
        val level = currentLevel ?: return
        world.tickVoxelAdapterSystem(level)
        MinecraftSystem.tickDataPrepareCommon(
            world,
            tickInventorySyncSystem = { tickClientInventorySyncSystem(this@tickDataPrepareSystem) }
        )
    }

    override fun GameSession.tickDataApplySystem() {
        val level = currentLevel ?: return
        world.tickGiveItemSystem()
        world.tickVoxelDoorSystem(level)
        tickOrientationTranslationSystem()
        tickWritableUiSystem()
        lightSystem.update(this)
        decalSystem.update(world)
        tickGenderSystem()
        audioManager.tick(this)
    }

    override fun GameSession.tickBulletFireSystem() {
        world.tickBulletFireSystem(currentLevel ?: return)
    }

    fun GameSession.tickOrientationTranslationSystem() {
        mainPlayer.apply<Orientation> {
            if (translationYaw != 0f || translationPitch != 0f) {
                camera.impulse(-translationYaw, -translationPitch)
            }
        }
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger("Engine Client")
    }
}
