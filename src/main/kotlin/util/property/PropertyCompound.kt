package org.lain.engine.util.property

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Сохраняет сериализованное имя типа до разделения серверной модели и DTO.
@Serializable
@SerialName("org.lain.engine.util.property.PropertyCompound")
sealed class PropertyCompound {
    open val isEmpty: Boolean = false

    @Serializable
    @SerialName("record")
    data class Record(
        val fields: Map<String, Property>,
    ) : PropertyCompound()

    @Serializable
    @SerialName("table")
    data class Table(val entries: List<Entry>) : PropertyCompound() {
        override val isEmpty: Boolean
            get() = entries.isEmpty()

        @Serializable
        data class Entry(
            val key: Property,
            val value: Property,
        )
    }

    @Serializable
    @SerialName("collection")
    data class Collection(val values: List<Property>) : PropertyCompound() {
        override val isEmpty: Boolean
            get() = values.isEmpty()
    }

    @Serializable
    @SerialName("player_physics")
    data class PlayerPhysics(val noClip: Boolean, val collides: List<String>) : PropertyCompound()

    @Serializable
    @SerialName("player")
    data class Player(val description: String) : PropertyCompound()
}
