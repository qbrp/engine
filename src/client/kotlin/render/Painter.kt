package org.lain.engine.client.render

data class EngineSprite(
    val path: String,
    val resolution: Int,
    val v1: Float = 0f,
    val u1: Float = 0f,
    val v2: Float = 1f,
    val u2: Float = 1f,
)