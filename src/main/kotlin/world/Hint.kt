package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.storage.Uuid

@Serializable
/**
 * Подсказки привязываются к блокам и игровым зонам
 * @see EngineChunk
 */
data class Hint(
    val texts: List<String>,
    val uuid: Uuid,
) {
    fun withText(text: String): Hint {
        return Hint(texts + text, uuid)
    }

    fun without(index: Int): Hint {
        val newTexts = texts.toMutableList()
        newTexts.removeAt(index)
        return Hint(newTexts, uuid)
    }

    fun displayText(index: Int): String {
        val text = texts[index]
        return "<gold>$index:</gold> <gray>$text"
    }
}

data class HintDestroyEvent(val uuid: Uuid) : Component