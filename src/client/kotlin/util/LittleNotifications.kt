package org.lain.engine.client.util

import org.lain.engine.client.render.EngineSprite
import org.lain.engine.util.Color
import org.lain.engine.util.DEFAULT_TEXT_COLOR

data class LittleNotification(
    val title: String,
    val description: String? = null,
    val color: Color = DEFAULT_TEXT_COLOR,
    val sprite: EngineSprite,
    val lifeTime: Long = 120,
    val transitionTime: Long = 12,
)

const val SPECTATOR_NOTIFICATION = "spectator"