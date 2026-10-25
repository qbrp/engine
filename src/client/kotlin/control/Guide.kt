package org.lain.engine.client.control

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.LittleNotification
import org.lain.engine.client.render.QUESTION
import org.lain.engine.util.Color
import org.lain.engine.util.file.FileSystem

@Serializable
data class GuideCompletion(
    var photoModeZoom: Boolean = false,
    var photoModeChatBubbles: Boolean = false
)

interface GuideMode {
    val isCompleted: Boolean
    fun start()
    fun stop(completion: GuideCompletion)
}

class PhotoModeGuide(guide: Guide) : GuideMode {
    private val client = guide.gameSession.client
    private val completion = guide.completion

    var zoomed: Boolean = guide.completion.photoModeZoom
        set(value) {
            field = value
            completion.photoModeZoom = value
            if (field) hideZoomNotification()
        }
    var toggledChatBubbles: Boolean = guide.completion.photoModeChatBubbles
        set(value) {
            field = value
            completion.photoModeChatBubbles = value
            if (field) hideChatBubblesNotification()
        }

    override val isCompleted: Boolean
        get() = zoomed && toggledChatBubbles

    override fun start() {
        client.showNotification(BASE, "photomode_base")
        if (!zoomed) client.showNotification(ZOOM, "photomode_zoom")
        if (!toggledChatBubbles) client.showNotification(CHAT_BUBBLES, "photomode_chat_bubbles")
    }

    override fun stop(completion: GuideCompletion) {
        completion.photoModeZoom = zoomed
        completion.photoModeChatBubbles = toggledChatBubbles
        client.removeLittleNotification("photomode_base")
        hideZoomNotification()
        hideChatBubblesNotification()
    }

    private fun hideZoomNotification() {
        client.removeLittleNotification("photomode_zoom")
    }

    private fun hideChatBubblesNotification() {
        client.removeLittleNotification("photomode_chat_bubbles")
    }

    companion object {
        val BASE = LittleNotification(
            "Фоторежим",
            """Эта опция является надстройкой над стандартным скрытием интерфейса игры и предназначена для создания скриншотов.""",
            color = Color.AQUA,
            lifeTime = null,
            sprite = QUESTION
        )
        val ZOOM = LittleNotification(
            "Приближение камеры",
            description =
                "Зажмите клавишу <aqua>CTRL</aqua> и крутите колёсиком мыши для приближения и отдаления. " +
                        "<newline>Для более точного контроля зажмите <aqua>LEFT ALT</aqua>.",
            color = Color.AQUA,
            lifeTime = null,
            sprite = QUESTION
        )
        val CHAT_BUBBLES = LittleNotification(
            "Чат-баблы",
            "Видимость чат-баблов регулируется клавишей <aqua>1</aqua>.",
            color = Color.AQUA,
            lifeTime = null,
            sprite = QUESTION
        )
    }
}

fun Guide.startPhotoMode() = tryStartGuide(PhotoModeGuide(this))

class Guide(val gameSession: GameSession) {
    val completion: GuideCompletion = FileSystem.clientGuide.let { completionFile ->
        val text = completionFile.readText()
        runCatching {
            if (text.isEmpty()) return@runCatching null
            Json.decodeFromString<GuideCompletion>(text)
        }
            .getOrNull()
            ?: GuideCompletion()
    }
    var mode: GuideMode? = null

    fun save() {
        FileSystem.clientGuide.writeText(Json.encodeToString(completion))
    }

    fun tick() {
        if (mode?.isCompleted == true) {
            mode?.stop(completion)
        }
    }

    fun <T : GuideMode> tryStartGuide(mode: T): T? {
        return if (this.mode == null && !mode.isCompleted) {
            this.mode = mode
            mode.start()
            mode
        } else {
            null
        }
    }

    inline fun <reified T : GuideMode> stopGuide() {
        val mode = mode
        if (mode is T) {
            mode.stop(completion)
            this.mode = null
        }
    }
}