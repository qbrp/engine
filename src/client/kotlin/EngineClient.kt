package org.lain.engine.client

import org.lain.engine.client.chat.ChatEventBus
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.render.TransformationsEditorScreen
import org.lain.engine.client.render.CD
import org.lain.engine.client.render.Camera
import org.lain.engine.client.render.EXCLAMATION
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.QUESTION
import org.lain.engine.client.render.ScreenRenderer
import org.lain.engine.client.render.Window
import org.lain.engine.client.render.ui.EngineUi
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.client.resources.ResourceManager
import org.lain.engine.client.util.SPECTATOR_NOTIFICATION
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.player.developerMode
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.util.DEV_MODE_COLOR
import org.lain.engine.util.SPECTATOR_MODE_COLOR
import org.lwjgl.glfw.GLFW

class EngineClient(
    val window: Window,
    val fontRenderer: FontRenderer,
    private val camera: Camera,
    val chatEventBus: ChatEventBus,
    val audioManager: EngineAudioManager,
    val ui: EngineUi,
    val eventBus: ClientEventBus
) {
    lateinit var options: EngineOptions
    val handler = ClientHandler(this, eventBus)
    val renderer = ScreenRenderer(this)
    val resourceManager = ResourceManager(this)

    val resources
        get() = resourceManager.context

    var developerMode: Boolean = false
        set(value) {
            gameSession?.let {
                it.mainPlayer.developerMode = value
                handler.onDeveloperModeUpdate(value)
            }
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
        gameSession.destroy()
        this.gameSession = null
    }
}