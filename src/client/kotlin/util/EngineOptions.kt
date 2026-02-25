package org.lain.engine.client.util

interface EngineOptions {
    val chatBubbleScale: Float
    val chatBubbleHeight: Float
    val chatBubbleLineWidth: Int
    val chatBubbleLifeTime: Int
    val chatBubbleBackgroundOpacity: Float
    val chatBubbleIgnoreLightLevel: Boolean
    val chatBubbles: Boolean
    val hideChatBubblesWithUi: Boolean
    val chatInputShakingForce: Float
    val chatInputShakingThreshold: Float
    val chatInputSendClosesChat: Boolean
    val chatFieldWidth: Int
    val chatFieldSize: Int
    val crosshairIndicatorVisible: Boolean
}
