package org.lain.engine.client.render.ui.chat

import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.chat.SpeechContent
import org.lain.engine.client.util.EngineOptions
import kotlin.random.Random

fun computeBaseShake(
    value: Float,
    base: Float,
    max: Float,
    threshold: Float,
): Float {
    val thresholdVolume = base + (max - base) * threshold
    if (thresholdVolume <= 0f) return 0f

    return (value - thresholdVolume)
        .coerceAtLeast(0f) / thresholdVolume
}

fun PlayerVolume.computeBaseShake(options: EngineOptions) =
    computeBaseShake(
        value,
        base,
        max,
        options.chatInputShakingThreshold,
    )

fun randomScreamTextTranslation(baseShake: Float, options: EngineOptions): Float {
    return Random.nextFloat() * baseShake * options.chatInputShakingForce
}

fun SpeechContent.randomScreamTextTranslation(gameSession: GameSession): Float {
    val volumes = gameSession.vocalRegulator.volume
    val options = gameSession.client.options
    return randomScreamTextTranslation(
        computeBaseShake(
            volume,
            volumes.base * 1.4f,
            volumes.max,
            options.chatInputShakingThreshold
        ),
        options
    )
}