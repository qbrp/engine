package org.lain.engine.client.util

import org.lain.engine.client.render.ui.chat.ChatOptions

interface Option<T> {
    fun get(): T
    fun set(value: T)
}

class ValidationException(message: String) : RuntimeException(message)

interface EngineOptions : ChatOptions {
    val chatBubbleScale: Float
    val chatBubbleHeight: Float
    val chatBubbleLineWidth: Int
    val chatBubbleLifeTime: Int
    val chatBubbleBackgroundOpacity: Float
    val chatBubbleIgnoreLightLevel: Boolean
    val chatBubbles: Boolean
    val chatInputShakingForce: Float
    val chatInputShakingThreshold: Float
    val chatInputSendClosesChat: Boolean

    val crosshairIndicatorVisible: Boolean
    val hotbarIndicatorsVisible: Boolean
    val labelEasingDistance: Int
    var autoLogin: Boolean
    val skinDownloadRetryDelay: Int
}