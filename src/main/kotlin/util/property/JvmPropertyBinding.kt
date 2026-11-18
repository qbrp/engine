package org.lain.engine.util.property

import org.lain.engine.util.copyWith
import kotlin.collections.get
import kotlin.collections.set
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

class JvmObjectPropertyBinding(
    override val value: Any
) : PropertyBinding {
    override fun child(segment: PropertyPath.Segment): PropertyBinding {
        val (propertyName, property) = liveProperty(segment)
        return property.get(value)?.propertyEditNode(property.returnType)
            ?: error("Object field $propertyName is null")
    }

    override fun setChild(
        segment: PropertyPath.Segment,
        newValue: PropertyInput?
    ): PropertyEditResult {
        val property = liveProperty(segment).second
        return replaceChild(segment, newValue?.toJvmValue(property.returnType))
    }

    override fun replaceChild(
        segment: PropertyPath.Segment,
        newValue: Any?
    ): PropertyEditResult {
        val (propertyName, property) = liveProperty(segment)
        val adaptedValue = newValue.wrapValueClasses(property.returnType)
        return if (property is KMutableProperty1<*, *>) {
            @Suppress("UNCHECKED_CAST")
            property as KMutableProperty1<Any, Any?>
            property.set(value, adaptedValue)
            PropertyEditResult.MutatedInPlace
        } else {
            PropertyEditResult.Replaced(value.copyWith(propertyName, adaptedValue))
        }
    }

    private fun liveProperty(segment: PropertyPath.Segment): Pair<String, KProperty1<Any, *>> {
        require(segment is PropertyPath.Segment.Field) { "Cannot access $value property by path segment $segment" }
        val propertyName = segment.name
        val liveProperty = value::class.memberProperties
            .find { it.name == propertyName } as? KProperty1<Any, *>
            ?: error("Property $propertyName not found on ${value::class.qualifiedName}")
        return propertyName to liveProperty
    }
}

class JvmListPropertyBinding(
    private val list: List<*>,
    private val elementType: KType?,
) : PropertyBinding, PropertyBinding.CollectionLike {
    override val value: Any
        get() = list
    override val collectionLike: PropertyBinding.CollectionLike?
        get() = this

    override fun addEntry(value: PropertyInput): PropertyEditResult {
        val elementType = elementType ?: error("Unsupported element type: $elementType")
        val element = value.toJvmValue(elementType)
        return editList { it.add(element) }
    }

    override fun child(segment: PropertyPath.Segment): PropertyBinding {
        require(segment is PropertyPath.Segment.Index)
        require(segment.index in list.indices) { "Index ${segment.index} is outside list bounds" }
        return list[segment.index]?.propertyEditNode(elementType)
            ?: error("List element ${segment.index} is null")
    }

    override fun setChild(
        segment: PropertyPath.Segment,
        newValue: PropertyInput?
    ): PropertyEditResult {
        val elementType = elementType ?: error("Unsupported element type: $elementType")
        return replaceChild(segment, newValue?.toJvmValue(elementType))
    }

    override fun replaceChild(
        segment: PropertyPath.Segment,
        newValue: Any?
    ): PropertyEditResult {
        require(segment is PropertyPath.Segment.Index)
        val index = segment.index
        require(index in list.indices) { "Index $index is outside list bounds" }
        return editList {
            it[index] = newValue?.let { value -> elementType?.let(value::wrapValueClasses) ?: value }
        }
    }

    private fun editList(mutation: (MutableList<Any?>) -> Unit): PropertyEditResult {
        return if (list is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            list as MutableList<Any?>
            try {
                mutation(list)
                PropertyEditResult.MutatedInPlace
            } catch (_: UnsupportedOperationException) {
                PropertyEditResult.Replaced(list.changed(mutation))
            }
        } else {
            PropertyEditResult.Replaced(list.changed(mutation))
        }
    }
}

class JvmMapPropertyBinding(
    private val map: Map<*, *>,
    private val keyType: KType?,
    private val valueType: KType?,
) : PropertyBinding, PropertyBinding.MapLike {
    override val value: Any
        get() = map
    override val mapLike: PropertyBinding.MapLike
        get() = this

    override fun child(segment: PropertyPath.Segment): PropertyBinding {
        val key = segment.tableKey()
        require(map.containsKey(key)) { "Map entry for key $key not found" }
        return map[key]?.propertyEditNode(valueType)
            ?: error("Map value for key $key is null")
    }

    override fun addEntry(key: PropertyInput, value: PropertyInput): PropertyEditResult {
        val keyType = keyType ?: error("Unsupported key type: $keyType")
        val valueType = valueType ?: error("Unsupported value type: $valueType")
        val keyValue = key.toJvmValue(keyType)
        val entryValue = value.toJvmValue(valueType)
        return edit { it[keyValue] = entryValue }
    }

    override fun setChild(
        segment: PropertyPath.Segment,
        newValue: PropertyInput?,
    ): PropertyEditResult {
        val valueType = valueType ?: error("Unsupported value type: $valueType")
        return replaceChild(segment, newValue?.toJvmValue(valueType))
    }

    override fun replaceChild(
        segment: PropertyPath.Segment,
        newValue: Any?,
    ): PropertyEditResult {
        val key = segment.tableKey()
        require(map.containsKey(key)) { "Map entry for key $key not found" }
        return edit {
            it[key] = newValue?.let { value -> valueType?.let(value::wrapValueClasses) ?: value }
        }
    }

    private fun edit(mutation: (MutableMap<Any?, Any?>) -> Unit): PropertyEditResult {
        if (map is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            map as MutableMap<Any?, Any?>
            try {
                mutation(map)
                return PropertyEditResult.MutatedInPlace
            } catch (_: UnsupportedOperationException) {
                // Kotlin read-only maps can still satisfy MutableMap on the JVM.
            }
        }
        return PropertyEditResult.Replaced(
            map.toMutableMap().apply { mutation(this) }.toMap()
        )
    }
}

private fun List<*>.changed(mutation: (MutableList<Any?>) -> Unit): List<Any?> {
    return toMutableList().apply { mutation(this) }.toList()
}

private fun PropertyPath.Segment.tableKey(): Any? {
    require(this is PropertyPath.Segment.EntryValue) { "Editing map keys is not supported" }
    return key
}
