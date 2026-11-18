package org.lain.engine.util.property

import kotlinx.serialization.Serializable

data class PropertyPath(val segments: List<Segment>) {
    fun withSegment(segment: Segment): PropertyPath {
        return copy(segments = segments + segment)
    }

    sealed interface Segment {
        data class Field(val name: String) : Segment
        data class Index(val index: Int) : Segment
        data class EntryKey(val key: Any?) : Segment
        data class EntryValue(val key: Any?) : Segment
    }
}

@Serializable
data class PropertyPathDto(val segments: List<Segment>) {
    fun withSegment(segment: Segment): PropertyPathDto {
        return copy(segments = segments + segment)
    }

    fun isDescendantOf(parent: PropertyPathDto): Boolean {
        return segments.size > parent.segments.size && segments.take(parent.segments.size) == parent.segments
    }

    @Serializable
    sealed interface Segment {
        @Serializable
        data class Field(val name: String) : Segment
        @Serializable
        data class Index(val index: Int) : Segment
        @Serializable
        data class EntryKey(val key: PropertyValue? = null) : Segment
        @Serializable
        data class EntryValue(val key: PropertyValue? = null) : Segment
    }
}

data class PropertyLocation(
    val live: PropertyPath = PropertyPath(emptyList()),
    val dto: PropertyPathDto = PropertyPathDto(emptyList()),
) {
    fun field(name: String) = PropertyLocation(
        live.withSegment(PropertyPath.Segment.Field(name)),
        dto.withSegment(PropertyPathDto.Segment.Field(name)),
    )

    fun index(index: Int) = PropertyLocation(
        live.withSegment(PropertyPath.Segment.Index(index)),
        dto.withSegment(PropertyPathDto.Segment.Index(index)),
    )

    fun entryKey(liveKey: Any?, propertyKey: PropertyValue?) = PropertyLocation(
        live.withSegment(PropertyPath.Segment.EntryKey(liveKey)),
        dto.withSegment(PropertyPathDto.Segment.EntryKey(propertyKey)),
    )

    fun entryValue(liveKey: Any?, propertyKey: PropertyValue?) = PropertyLocation(
        live.withSegment(PropertyPath.Segment.EntryValue(liveKey)),
        dto.withSegment(PropertyPathDto.Segment.EntryValue(propertyKey)),
    )
}
