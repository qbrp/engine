package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable

@Serializable
data class InteractionSelection(
    val title: String,
    val variants: List<Variant>
) {
    @Serializable
    data class Variant(
        val id: String,
        val name: String,
        val asset: String,
        var isItem: Boolean = false
    )
}