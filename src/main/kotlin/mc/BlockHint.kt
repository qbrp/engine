package org.lain.engine.mc

import kotlinx.serialization.Serializable

@Serializable
data class BlockHint(
    val title: String,
    val texts: MutableList<String> = mutableListOf()
) {
    fun displayText(index: Int): String {
        val text = texts[index]
        return "<gold>$index:</gold> <gray>$text"
    }
}