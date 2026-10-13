package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class InspectionObject {
    open val isEmpty: Boolean = false

    @Serializable
    @SerialName("record")
    data class Record(
        val fields: Map<String, InspectionNode>,
    ) : InspectionObject()

    @Serializable
    @SerialName("table")
    data class Table(val entries: List<Entry>) : InspectionObject() {
        override val isEmpty: Boolean
            get() = entries.isEmpty()

        @Serializable
        data class Entry(
            val key: InspectionNode,
            val value: InspectionNode,
        )
    }

    @Serializable
    @SerialName("player_physics")
    data class PlayerPhysics(val noClip: Boolean, val collides: List<String>) : InspectionObject()

    @Serializable
    @SerialName("collection")
    data class Collection(val values: List<InspectionNode>) : InspectionObject() {
        override val isEmpty: Boolean
            get() = values.isEmpty()
    }
}