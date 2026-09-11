package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.script.EngineId
import org.lain.engine.script.Identifiable

data class ProgressionType(
    val duration: Int,
    val animation: ProgressionAnimation
)

@JvmInline
@Serializable
value class ProgressionAnimationId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString() = value.toString()
}

data class ProgressionAnimation(
    val frames: List<String>,
    val progressionText: String,
    val successText: String
) {
    companion object {
        val DEFAULT = ProgressionAnimation(
            listOf(),
            "Выполнение действия...",
            "Действие выполнено"
        )
    }
}

data class Progression(
    val type: ProgressionType,
    var timeElapsed: Int = 0,
    var placeholders: MutableMap<String, String> = mutableMapOf(),
    var text: String? = null,
) : Component {
    val progress: Float
        get() = (timeElapsed.toFloat() / type.duration).coerceIn(0f, 1f)
}