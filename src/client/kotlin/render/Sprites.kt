package org.lain.engine.client.render

//32x32
val QUESTION = EngineSprite("textures/icon/question.png", 32)
val EXCLAMATION = EngineSprite("textures/icon/exclamation.png", 32)
val WARNING = EngineSprite("textures/icon/warning.png", 32)
val CD = EngineSprite("textures/icon/cd.png", 32)
val VOICE_WARNING = EngineSprite("textures/icon/voice_warning.png", 32)
val VOICE_BROKEN = EngineSprite("textures/icon/voice_broken.png", 32)
val HAND = EngineSprite("textures/icon/hand.png", 32)

// 16x16
val EXCLAMATION_RED = EngineSprite("textures/icon/exclamation_red.png", 16)
val MENTION = EngineSprite("textures/icon/mention.png", 16)

data class EngineSprite(
    val path: String,
    val resolution: Int,
    val v1: Float = 0f,
    val u1: Float = 0f,
    val v2: Float = 1f,
    val u2: Float = 1f,
)

data class Rect2(val x1: Float, val y1: Float, val x2: Float, val y2: Float)