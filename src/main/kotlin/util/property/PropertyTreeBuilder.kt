package org.lain.engine.util.property

import org.lain.engine.util.ecs.ComponentWorld
import kotlin.collections.set
import kotlin.reflect.KType
import java.util.IdentityHashMap

class PropertyTreeBuilder(
    val world: ComponentWorld,
    var lastReferenceId: Int = 0,
    var lastTypeDescriptorId: Int = 0,
    val visited: MutableMap<Any, PropertyReference> = IdentityHashMap(),
    val properties: MutableMap<PropertyPathDto, Property> = mutableMapOf(),
    val livePaths: MutableMap<PropertyPathDto, PropertyPath> = mutableMapOf(),
    val compounds: MutableMap<PropertyReference, PropertyCompound> = mutableMapOf(),
    val typeDescriptors: MutableMap<PropertyTypeDescriptorId, PropertyTypeDescriptor> = mutableMapOf(),
    val kTypeDescriptors: MutableMap<KType, PropertyTypeDescriptorId> = mutableMapOf(),
) {
    private val buildingReferences = mutableSetOf<PropertyReference>()

    fun reserveReference(identity: Any): PropertyValue.Reference {
        val reference = visited[identity] ?: PropertyReference(lastReferenceId++).also {
            visited[identity] = it
        }
        return PropertyValue.Reference(reference)
    }

    fun buildReference(identity: Any, transformer: () -> PropertyCompound): PropertyValue.Reference {
        val reference = reserveReference(identity)
        val id = reference.objectId
        if (id in compounds || !buildingReferences.add(id)) return reference

        compounds[id] = transformer()
        buildingReferences.remove(id)
        return reference
    }

    fun property(
        value: PropertyValue?,
        location: PropertyLocation,
        type: PropertyTypeDescriptorId
    ): Property {
        return Property(value, location.dto, type)
            .also { property ->
                properties.putIfAbsent(location.dto, property)
                    ?.let { existingProperty -> error("Свойство $existingProperty уже отведено под путь ${location.dto}") }
                livePaths[location.dto] = location.live
            }
    }

    // После сборки построитель и его коллекции больше не изменяются.
    fun build(): ServerPropertyTree = ServerPropertyTree(
        PropertyTree(properties, compounds, typeDescriptors),
        livePaths,
    )

    fun type(kType: KType, createDescriptor: () -> PropertyTypeDescriptor): PropertyTypeDescriptorId {
        kTypeDescriptors[kType]?.let { return it }

        val id = PropertyTypeDescriptorId(lastTypeDescriptorId++)
        kTypeDescriptors[kType] = id
        typeDescriptors[id] = PropertyTypeDescriptor.Unknown
        typeDescriptors[id] = createDescriptor()

        return id
    }

    fun type(descriptor: PropertyTypeDescriptor): PropertyTypeDescriptorId {
        val id = PropertyTypeDescriptorId(lastTypeDescriptorId++)
        typeDescriptors[id] = descriptor
        return id
    }

    fun unknownType(): PropertyTypeDescriptorId {
        typeDescriptors.putIfAbsent(UNKNOWN_TYPE_ID, PropertyTypeDescriptor.Unknown)
        return UNKNOWN_TYPE_ID
    }

    companion object {
        private val UNKNOWN_TYPE_ID = PropertyTypeDescriptorId(-1)
    }
}

context(ctx: PropertyTreeBuilder)
fun <T : Any> T.reference(
    identity: Any = this,
    transformer: context(PropertyTreeBuilder) () -> PropertyCompound
): PropertyValue.Reference {
    return ctx.buildReference(identity) { transformer() }
}
