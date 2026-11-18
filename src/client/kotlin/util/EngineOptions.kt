package org.lain.engine.client.util

sealed interface Option<T> {
    fun get(): T
}

interface ScalarOption<T> : Option<T> {
    fun validate(value: T): Validation<T>
    fun set(value: T)
}

interface RecordOption<T> : Option<T> {
    val fields: List<Option<*>>
}

sealed interface Validation<T> {
    class Success<T> : Validation<T>
    data class Corrected<T>(val value: T) : Validation<T>
    data class Fail<T>(val error: String) : Validation<T>
}

interface ChatOptions {
    val chatBackgroundOpacity: Float
    val chatTextOpacity: Float
    val activeChatPx: Int
    val unactiveChatPx: Int
    val chatWidth: Int
    val chatSize: Int
    val chatMessageIndent: Float
}

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