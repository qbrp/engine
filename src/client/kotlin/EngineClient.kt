package org.lain.engine.client

import org.lain.engine.client.chat.ChatEventBus
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.*
import org.lain.engine.client.render.ui.EngineUi
import org.lain.engine.client.resources.ResourceManager
import org.lain.engine.client.util.*
import org.lain.engine.player.developerMode
import org.lain.engine.util.DEV_MODE_COLOR
import org.lain.engine.util.SPECTATOR_MODE_COLOR

class EngineClient(
    val window: Window,
    val fontRenderer: FontRenderer,
    val camera: Camera,
    val chatEventBus: ChatEventBus,
    val audioManager: EngineAudioManager,
    val ui: EngineUi,
    val eventBus: ClientEventBus,
) {
    lateinit var options: EngineOptions
    lateinit var thread: Thread
    var savedState: SavedState? = null
    val handler = ClientHandler(this, eventBus)
    val renderer = ScreenRenderer(this)
    val resourceManager = ResourceManager(this)

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
            val text = if (value) "Включена" else "Выключена"

            applyLittleNotification(
                LittleNotification(
                    "Отладка акустики",
                    text,
                    DEV_MODE_COLOR,
                    VOICE_WARNING
                )
            )
            handler.onDeveloperModeUpdate(developerMode, value)

            field = value
        }

    var gameSession: GameSession? = null
    val gameSessionActive
        get() = gameSession != null

    var ticks = 0L
        private set

    fun tick() {
        ticks += 1
        gameSession?.tick()
        handler.tick()
        eventBus.tick()
    }

    fun isOnThread() = Thread.currentThread() == thread

    fun execute(r: () -> Unit) {
        handler.taskExecutor.add("Unnamed task", r)
    }

    fun toggleHudHiding() {
        renderer.hudHidden = !renderer.hudHidden
    }

    fun toggleDeveloperMode() {
        developerMode = !developerMode
        applyLittleNotification(
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
        if (MinecraftClient.currentScreen != null) return
        gameSession?.movementManager?.roll(delta)
    }

    fun sendSpectatingNotification() {
        applyLittleNotification(
            LittleNotification(
                "Наблюдение",
                "Введите команду /spawn для появления",
                SPECTATOR_MODE_COLOR,
                sprite = QUESTION,
                lifeTime = 200
            ),
            SPECTATOR_NOTIFICATION
        )
    }

    fun applyLittleNotification(notification: LittleNotification, slot: String? = null) {
        renderer.littleNotificationsRenderer.create(notification, slot)
        audioManager.playUiNotificationSound()
    }

    fun removeLittleNotification(slot: String) {
        renderer.littleNotificationsRenderer.removeNotification(slot)
    }

    suspend fun joinGameSession(gameSession: GameSession) {
        val reload = resourceManager.reload(gameSession)
        reload.join()
        this.gameSession = gameSession
        sendSpectatingNotification()
    }

    fun leaveGameSession() {
        val gameSession = gameSession ?: error("Game session is not active")
        savedState = SavedState(gameSession.server, gameSession.world.chunkStorage)
        gameSession.destroy()
        this.gameSession = null
    }
}