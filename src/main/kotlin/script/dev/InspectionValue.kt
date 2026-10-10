package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class InspectionValue {
    @Serializable
    @SerialName("ref")
    data class Reference(val id: Int) : InspectionValue()

    @Serializable
    @SerialName("primitive")
    data class Primitive(
        val readonly: Boolean,
        val primitive: InspectionPrimitive
    ) : InspectionValue()

    @Serializable
    @SerialName("null")
    object Null : InspectionValue()
}