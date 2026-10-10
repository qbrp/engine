package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class InspectionObject {
    open val isEmpty: Boolean = false

    @Serializable
    @SerialName("table")
    data class Table(val values: Map<String, InspectionValue>) : InspectionObject() {
        override val isEmpty: Boolean
            get() = values.isEmpty()
    }

    @Serializable
    @SerialName("player_physics")
    data class PlayerPhysics(val noClip: Boolean, val collides: List<String>) : InspectionObject()

    @Serializable
    @SerialName("collection")
    data class Collection(val values: List<InspectionValue>) : InspectionObject() {
        override val isEmpty: Boolean
            get() = values.isEmpty()
    }
}
