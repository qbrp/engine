package org.lain.engine.world

import kotlinx.serialization.Serializable

@Serializable
data class BlockHint(val texts: List<String>) {
    fun withText(text: String): BlockHint {
        return BlockHint(texts + text)
    }

    fun without(index: Int): BlockHint {
        val newTexts = texts.toMutableList()
        newTexts.removeAt(index)
        return BlockHint(newTexts)
    }

    fun displayText(index: Int): String {
        val text = texts[index]
        return "<gold>$index:</gold> <gray>$text"
    }
}