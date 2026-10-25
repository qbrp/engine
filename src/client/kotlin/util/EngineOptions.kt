package org.lain.engine.client.util

interface Option<T> {
    fun get(): T
    fun set(value: T)
}

class ValidationException(message: String) : RuntimeException(message)

interface EngineOptions {
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
    val chatFieldWidth: Int
    val chatFieldSize: Int
    val crosshairIndicatorVisible: Boolean
    val hotbarIndicatorsVisible: Boolean
    val labelEasingDistance: Int
    var autoLogin: Boolean
    val skinDownloadRetryDelay: Int
}