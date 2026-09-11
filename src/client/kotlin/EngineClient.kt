package org.lain.engine.client

import org.lain.engine.client.account.AccountManager
import org.lain.engine.client.account.ClientEngineAccountService
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.chat.ChatEventBus
import org.lain.engine.client.control.onScrollInspection
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.handler.GameSessionJoinFlow
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.*
import org.lain.engine.client.render.legacy.EngineUi
import org.lain.engine.client.resources.ResourceManager
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.client.EngineOptions
import org.lain.engine.client.render.LittleNotification
import org.lain.engine.client.render.showAcousticDebugNotification
import org.lain.engine.player.developerMode
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.server.account.EngineHttpClient
import org.lain.engine.util.DEV_MODE_COLOR

class EngineClient(
    val window: Window,
    val camera: Camera,
    val chatEventBus: ChatEventBus,
    val audioManager: EngineAudioManager,
    val ui: EngineUi,
    val infrastructure: ClientPlatform,
    httpClient: EngineHttpClient,
) {
    lateinit var options: EngineOptions
    lateinit var thread: Thread
    val handler = ClientHandler(this, infrastructure)
    val renderer = ScreenRenderer(this)
    val moduleManager = ModuleManager()
    val resourceManager = ResourceManager(this)
    val skinTextureManager = SkinTextureManager(httpClient.rest)
    val accountManager: AccountManager =
        AccountManager(skinTextureManager, ClientEngineAccountService(httpClient))

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

    fun toggleHudHiding() {
        renderer.hudHidden = !renderer.hudHidden
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
            )
        )
    }

    fun onScroll(delta: Float) {
        if (MinecraftClient.screen != null) return
        gameSession?.let { gameSession ->
            val inspection = gameSession.inspection
            if (inspection.concentration) {
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
        gameSession.destroy()
        this.gameSession = null
    }
}