package org.lain.engine.util.property

import kotlinx.serialization.Serializable

@Serializable
data class PropertyTree(
    val properties: Map<PropertyPathDto, Property>,
    val compounds: Map<PropertyReference, PropertyCompound>,
    val types: Map<PropertyTypeDescriptorId, PropertyTypeDescriptor>,
)

data class ServerPropertyTree(
    val tree: PropertyTree,
    val livePaths: Map<PropertyPathDto, PropertyPath>,
)
