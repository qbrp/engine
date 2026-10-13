package org.lain.engine.script.dev

import kotlin.collections.set
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

class JvmObjectEditNode(
    override val value: Any
) : InspectionEditNode {
    override fun segment(property: InspectionPrimitive): InspectionPath.Segment {
        val name = (property as? InspectionPrimitive.Str)?.string
            ?: error("JVM object property must be a string")
        return InspectionPath.Segment.Field(name)
    }

    override fun child(segment: InspectionPath.Segment): InspectionEditNode {
        val (propertyName, property) = liveProperty(segment)
        return property.get(value)?.toInspectionEditNode(property.returnType)
            ?: error("Object field $propertyName is null")
    }

    override fun setChild(
        segment: InspectionPath.Segment,
        newValue: InputValue?
    ): InspectionEditResult {
        val property = liveProperty(segment).second
        return replaceChild(segment, newValue?.toJvmValue(property.returnType))
    }

    override fun replaceChild(
        segment: InspectionPath.Segment,
        newValue: Any?
    ): InspectionEditResult {
        val (propertyName, property) = liveProperty(segment)
        val adaptedValue = newValue.adaptTo(property.returnType)
        return if (property is KMutableProperty1<*, *>) {
            @Suppress("UNCHECKED_CAST")
            property as KMutableProperty1<Any, Any?>
            property.set(value, adaptedValue)
            InspectionEditResult.MutatedInPlace
        } else {
            InspectionEditResult.Replaced(value.copyWith(propertyName, adaptedValue))
        }
    }

    private fun liveProperty(segment: InspectionPath.Segment): Pair<String, KProperty1<Any, *>> {
        require(segment is InspectionPath.Segment.Field)
        val propertyName = segment.name
        val liveProperty = value::class.memberProperties
            .find { it.name == propertyName } as? KProperty1<Any, *>
            ?: error("Property $propertyName not found on ${value::class.qualifiedName}")
        return propertyName to liveProperty
    }
}

class JvmListEditNode(
    private val list: List<*>,
    private val elementType: KType?,
) : InspectionEditNode, InspectionEditNode.CollectionLike {
    override val value: Any
        get() = list
    override val collectionLike: InspectionEditNode.CollectionLike?
        get() = this

    override fun segment(property: InspectionPrimitive): InspectionPath.Segment {
        val index = (property as? InspectionPrimitive.Int)?.int
            ?: error("List index must be an integer")
        return InspectionPath.Segment.Index(index)
    }

    override fun addEntry(value: InputValue): InspectionEditResult {
        val elementType = elementType ?: error("Unsupported element type: $elementType")
        val element = value.toJvmValue(elementType).adaptTo(elementType)
        return editList { it.add(element) }
    }

    override fun child(segment: InspectionPath.Segment): InspectionEditNode {
        require(segment is InspectionPath.Segment.Index)
        require(segment.index in list.indices) { "Index ${segment.index} is outside list bounds" }
        return list[segment.index]?.toInspectionEditNode(elementType)
            ?: error("List element ${segment.index} is null")
    }

    override fun setChild(
        segment: InspectionPath.Segment,
        newValue: InputValue?
    ): InspectionEditResult {
        val elementType = elementType ?: error("Unsupported element type: $elementType")
        return replaceChild(segment, newValue?.toJvmValue(elementType))
    }

    override fun replaceChild(
        segment: InspectionPath.Segment,
        newValue: Any?
    ): InspectionEditResult {
        require(segment is InspectionPath.Segment.Index)
        val index = segment.index
        require(index in list.indices) { "Index $index is outside list bounds" }
        return editList {
            it[index] = newValue?.let { value -> elementType?.let(value::adaptTo) ?: value }
        }
    }

    private fun editList(mutation: (MutableList<Any?>) -> Unit): InspectionEditResult {
        return if (list is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            list as MutableList<Any?>
            try {
                mutation(list)
                InspectionEditResult.MutatedInPlace
            } catch (_: UnsupportedOperationException) {
                InspectionEditResult.Replaced(list.changed(mutation))
            }
        } else {
            InspectionEditResult.Replaced(list.changed(mutation))
        }
    }
}

class JvmMapEditNode(
    private val map: Map<*, *>,
    private val keyType: KType?,
    private val valueType: KType?,
) : InspectionEditNode, InspectionEditNode.MapLike {
    override val value: Any
        get() = map
    override val mapLike: InspectionEditNode.MapLike
        get() = this

    override fun segment(property: InspectionPrimitive): InspectionPath.Segment {
        val key = property.toJvmValue()
        require(map.containsKey(key)) { "Map entry for key $key not found" }
        return InspectionPath.Segment.TableEntry(map.keys.first { it == key })
    }

    override fun child(segment: InspectionPath.Segment): InspectionEditNode {
        val key = segment.tableKey()
        require(map.containsKey(key)) { "Map entry for key $key not found" }
        return map[key]?.toInspectionEditNode(valueType)
            ?: error("Map value for key $key is null")
    }

    override fun addEntry(key: InputValue, value: InputValue): InspectionEditResult {
        val keyType = keyType ?: error("Unsupported key type: $keyType")
        val valueType = valueType ?: error("Unsupported value type: $valueType")
        val keyValue = key.toJvmValue(keyType).adaptTo(keyType)
        val entryValue = value.toJvmValue(valueType).adaptTo(valueType)
        return edit { it[keyValue] = entryValue }
    }

    override fun setChild(
        segment: InspectionPath.Segment,
        newValue: InputValue?,
    ): InspectionEditResult {
        val valueType = valueType ?: error("Unsupported value type: $valueType")
        return replaceChild(segment, newValue?.toJvmValue(valueType))
    }

    override fun replaceChild(
        segment: InspectionPath.Segment,
        newValue: Any?,
    ): InspectionEditResult {
        val key = segment.tableKey()
        require(map.containsKey(key)) { "Map entry for key $key not found" }
        return edit {
            it[key] = newValue?.let { value -> valueType?.let(value::adaptTo) ?: value }
        }
    }

    private fun edit(mutation: (MutableMap<Any?, Any?>) -> Unit): InspectionEditResult {
        if (map is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            map as MutableMap<Any?, Any?>
            try {
                mutation(map)
                return InspectionEditResult.MutatedInPlace
            } catch (_: UnsupportedOperationException) {
                // Kotlin read-only maps can still satisfy MutableMap on the JVM.
            }
        }
        return InspectionEditResult.Replaced(
            map.toMutableMap().apply { mutation(this) }.toMap()
        )
    }
}

private fun List<*>.changed(mutation: (MutableList<Any?>) -> Unit): List<Any?> {
    return toMutableList().apply { mutation(this) }.toList()
}

private fun InspectionPath.Segment.tableKey(): Any? {
    require(this is InspectionPath.Segment.TableEntry)
    return key
}

private fun Any?.adaptTo(type: KType): Any? {
    if (this == null) return null
    val valueClass = type.classifier as? KClass<*> ?: return this
    if (!valueClass.isValue || valueClass.isInstance(this)) return this

    val constructor = valueClass.primaryConstructor
        ?: error("Value class ${valueClass.qualifiedName} has no primary constructor")
    val parameter = constructor.parameters.single()
    return constructor.call(adaptTo(parameter.type))
}
