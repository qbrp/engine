package org.lain.engine.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.lain.engine.client.account.AccountManager
import org.lain.engine.client.account.ClientEngineAccountService
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.chat.ChatEventBus
import org.lain.engine.client.control.onScrollInspection
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.handler.GameSessionJoinFlow
import org.lain.engine.client.handler.ServerPlayState
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.isAltDown
import org.lain.engine.client.mc.isControlDown
import org.lain.engine.client.render.*
import org.lain.engine.client.render.ui.ImGuiManager
import org.lain.engine.client.resources.AssetCompilation
import org.lain.engine.client.resources.ResourceManager
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.developerMode
import org.lain.engine.player.get
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.server.account.EngineHttpClient
import org.lain.engine.util.DEV_MODE_COLOR

class EngineClient(
    val window: Window,
    val camera: Camera,
    val chatEventBus: ChatEventBus,
    val audioManager: EngineAudioManager,
    val platform: ClientPlatform,
    httpClient: EngineHttpClient,
) {
    lateinit var options: EngineOptions
    lateinit var thread: Thread
    val handler = ClientHandler(this, platform)
    val renderer = ScreenRenderer(this)
    val moduleManager = ModuleManager()
    val resourceManager = ResourceManager(this)
    val skinTextureManager = SkinTextureManager(httpClient.rest)
    val accountManager: AccountManager =
        AccountManager(skinTextureManager, ClientEngineAccountService(httpClient))
    val imGuiManager = ImGuiManager()

    val resources
        get() = resourceManager.context

    var developerMode: Boolean = false
        set(value) {
            gameSession?.let {
                it.mainPlayer.developerMode = value
                handler.onDeveloperModeUpdate(value, acousticDebug)
            }
            field = value
        }
    var acousticDebug: Boolean = false
        set(value) {
            if (gameSession == null) return
            showAcousticDebugNotification(value)
            handler.onDeveloperModeUpdate(developerMode, value)
            field = value
        }

    var assetCompilation: AssetCompilation? = null
        private set

    var gameSession: GameSession? = null
    val gameSessionActive
        get() = gameSession != null

    val luaDataStorage = LuaDataStorage()

    val connectionState
        get() = accountManager.state

    val canPlaySingleplayer
        get() = accountManager.lastAccountResponse != null

    val canPlayMultiplayer
        get() = accountManager.authorized

    init {
        accountManager.autoLoginAsync()
    }

    fun startAssetCompilation() {
        assetCompilation?.scope?.cancel()
        assetCompilation = AssetCompilation(
            moduleManager,
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }

    fun onOptionsUpdate() {
        skinTextureManager.onOptions(options)
    }

    var joinFlow: GameSessionJoinFlow? = null
        private set

    fun startJoinFlow(joinType: GameSessionJoinFlow.JoinType) {
        joinFlow = GameSessionJoinFlow(joinType, this, handler)
    }

    fun stopJoinFlow() {
        joinFlow?.cancel()
        joinFlow = null
    }

    var ticks = 0L
        private set

    fun tick() {
        ticks += 1
        handler.tick()
        gameSession?.tick()
        handler.postTick()
    }

    fun execute(r: () -> Unit) {
        handler.taskExecutor.add("Unnamed task", r)
    }

    fun setHudHiding(value: Boolean) {
        val options = MinecraftClient.options
        options.hideGui = value
        renderer.hudHidden = options.hideGui
    }

    fun toggleDeveloperMode() {
        developerMode = !developerMode
        showNotification(
            LittleNotification(
                "Режим разработчика",
                if (developerMode) {
                    "Включен. Доступны экспериментальные функции и утилиты для дебага."
                } else {
                    "Выключен."
                },
                DEV_MODE_COLOR,
                sprite = if (developerMode) QUESTION else EXCLAMATION,
                lifeTime = 100
            ),
            "dev_mode"
        )
    }

    fun onScroll(delta: Float) {
        if (MinecraftClient.screen != null) return
        gameSession?.let { gameSession ->
            val inspection = gameSession.inspection
            val photoMode = gameSession.photoMode
            if (photoMode != null) {
                val controlDown = isControlDown()
                val altDown = isAltDown()
                if (controlDown || altDown) photoMode.zoom(delta, altDown)
            } else if (inspection.concentration) {
                onScrollInspection(inspection, delta)
            } else {
                gameSession.movementManager.roll(delta)
            }
        }
    }

    fun showNotification(notification: LittleNotification, slot: String? = null) {
        renderer.littleNotificationsRenderer.create(notification, slot)
        audioManager.playUiNotificationSound()
    }

    fun removeLittleNotification(slot: String) {
        renderer.littleNotificationsRenderer.removeNotification(slot)
    }

    fun joinGameSession(gameSession: GameSession) {
        this.gameSession = gameSession
    }

    fun leaveGameSession() {
        val gameSession = gameSession ?: error("Game session is not active")
        val characterId = gameSession.mainPlayer.get<AppliedCharacter>()?.character?.profile?.id
        val serverId = gameSession.server
        imGuiManager.closeAll()
        runBlocking { ServerPlayState(characterId).save(serverId) }
        gameSession.destroy()
        skinTextureManager.cancelDownloadTasks()
        this.gameSession = null
    }
}
