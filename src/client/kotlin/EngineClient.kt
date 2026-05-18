package org.lain.engine.client

import org.lain.engine.client.chat.ChatEventBus
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.*
import org.lain.engine.client.render.legacy.EngineUi
import org.lain.engine.client.resources.ResourceManager
import org.lain.engine.client.script.ClientLuaContext
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.client.util.SPECTATOR_NOTIFICATION
import org.lain.engine.player.developerMode
import org.lain.engine.script.*
import org.lain.engine.script.lua.FileScriptSource
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.script.lua.LuaDependencies
import org.lain.engine.server.ServerId
import org.lain.engine.util.DEV_MODE_COLOR
import org.lain.engine.util.SPECTATOR_MODE_COLOR
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

class EngineClient(
    val window: Window,
    val camera: Camera,
    val chatEventBus: ChatEventBus,
    val audioManager: EngineAudioManager,
    val ui: EngineUi,
    val eventBus: ClientEventBus,
) {
    val namespacedStorage: NamespacedStorageAccess = ThreadSafeNamespaceStorageAccessImpl(emptyNamespacedStorage())
    lateinit var options: EngineOptions
    lateinit var thread: Thread
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

    private val luaDataStorage = LuaDataStorage()
    var compilationResult: CompilationResult? = null
    var luaContext: ClientLuaContext? = null

    fun compileScripts(): CompilationResult {
        val contentsPath = resources.contents.file
        val result = compileContents(contentsPath, luaContext ?: error("Lua context not initialized"))
        compilationResult = result
        namespacedStorage.loadContentsCompileResult(result)
        return compilationResult!!
    }

    fun createLuaDependencies(scriptsPath: File): LuaDependencies {
        return LuaDependencies(
            JsePlatform.standardGlobals(),
            namespacedStorage,
            scriptsPath.path,
            luaDataStorage
        )
    }

    fun createLuaContext(serverId: ServerId): ClientLuaContext {
        val scriptsPath = resources.scripts.file
        return ClientLuaContext(
            this,
            FileScriptSource(scriptsPath.luaEntrypointDir(serverId)),
            createLuaDependencies(scriptsPath),
        ).also {
            luaContext = it
            it.setup()
        }
    }

    var ticks = 0L
        private set

    fun tick() {
        ticks += 1
        handler.tick()
        gameSession?.tick()
        handler.postTick()
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
        if (MinecraftClient.screen != null) return
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
        //compilationResult = null
        //с богом
        this.gameSession = null
    }
}