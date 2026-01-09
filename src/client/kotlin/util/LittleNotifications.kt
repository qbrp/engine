package org.lain.engine.client.util

import org.lain.engine.client.render.EngineSprite
import org.lain.engine.util.Color
import org.lain.engine.util.DEFAULT_TEXT_COLOR
import org.lain.engine.util.text.EngineText
import org.lain.engine.util.text.EngineTextStyle

data class LittleNotification(
    val title: String,
    val description: String? = null,
    val color: Color = DEFAULT_TEXT_COLOR,
    val sprite: EngineSprite,
    val lifeTime: Long = 120,
    val transitionTime: Long = 12,
) {
    val titleTextNode = EngineText(
        content = title,
        style = EngineTextStyle(color = color),
    )
    val descriptionTextNode = description?.let {
        EngineText(it)
    }
}

const val SPECTATOR_NOTIFICATION = "spectator"

