package org.lain.engine.util.property

import kotlinx.serialization.Serializable

@Serializable
data class Property(
    val value: PropertyValue? = null,
    val path: PropertyPathDto,
    val type: PropertyTypeDescriptorId,
) {
    fun child(segment: PropertyPathDto.Segment) = copy(path = path.withSegment(segment))
}
