package org.lain.engine.client.util

import org.lain.engine.client.render.DEFAULT_TEXT_COLOR
import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.EngineText

data class LittleNotification(
    val title: String,
    val description: String? = null,
    val color: Int = DEFAULT_TEXT_COLOR,
    val sprite: EngineSprite? = null,
    val lifeTime: Long = 82,
    val transitionTime: Long = 12,
) {
    val titleTextNode = EngineText(
        content = title,
        color = color,
    )
    val descriptionTextNode = description?.let {
        EngineText(
            content = it,
            scale = 0.6f
        )
    }
}

const val SPECTATOR_NOTIFICATION = "spectator"

