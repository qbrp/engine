package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class InspectionObject {
    open val isEmpty: Boolean = false
    abstract fun getValue(key: String): InspectionValue?

    @Serializable
    @SerialName("table")
    data class Table(val values: Map<String, InspectionValue>) : InspectionObject() {
        override val isEmpty: Boolean
            get() = values.isEmpty()

        override fun getValue(key: String): InspectionValue? {
            return values[key]
        }
    }

    @Serializable
    @SerialName("player_physics")
    data class PlayerPhysics(val noClip: Boolean, val collides: List<String>) : InspectionObject() {
        override fun getValue(key: String): InspectionValue? {
            return null
        }
    }

    @Serializable
    @SerialName("collection")
    data class Collection(val values: List<InspectionValue>) : InspectionObject() {
        override val isEmpty: Boolean
            get() = values.isEmpty()
        override fun getValue(key: String): InspectionValue? {
            return values.getOrNull(key.toInt())
        }
    }
}
