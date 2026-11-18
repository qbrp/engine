package org.lain.engine.util.property

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PropertyReference(val value: Int)

@Serializable
sealed class PropertyValue {
    @Serializable
    @SerialName("ref")
    data class Reference(val objectId: PropertyReference) : PropertyValue()

    @Serializable
    @SerialName("scalar")
    data class Primitive(val primitive: PropertyScalar) : PropertyValue()

}
