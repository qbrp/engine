package org.lain.engine.client.control

import org.lain.engine.client.GameSession

class PhotoMode(private val gameSession: GameSession) {
    private val client = gameSession.client
    var showChatBubbles: Boolean = false
        set(value) {
            field = value
            client.renderer.emitPhotoModeChatBubbleCaption(value)
        }
    var zoom: Float = 1f

    init {
        client.setHudHiding(true)
        client.renderer.emitPhotoModeOnCaption(true)
    }

    fun disable() {
        client.setHudHiding(false)
        client.renderer.emitPhotoModeOnCaption(false)
    }

    fun zoom(value: Float, control: Boolean) {
        val adjustValue = if (control) 0.4f else 0.05f
        val highZoomMultiplier = zoom / MAX_ZOOM * 5f
        zoom = (zoom + (value * adjustValue * highZoomMultiplier)).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    companion object {
        const val MAX_ZOOM = 10f
        const val MIN_ZOOM = 0.7f
    }
}

fun GameSession.togglePhotoMode() {
    if (photoMode == null) {
        photoMode = PhotoMode(this)
    } else {
        photoMode?.disable()
        photoMode = null
    }
}