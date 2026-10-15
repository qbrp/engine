package org.lain.engine.script.dev

import kotlinx.serialization.Serializable
import org.lain.engine.script.SList
import org.lain.engine.script.SNil
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValue
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

@Serializable
sealed interface InputValue {
    @Serializable
    data class Primitive(val primitive: InspectionPrimitive) : InputValue

    data class Map(val entries: List<Entry>) : InputValue {
        @Serializable
        data class Entry(
            val key: InputValue,
            val value: InputValue,
        )
    }

    data class Collection(val elements: List<InputValue>) : InputValue

    data class Record(val fields: List<Field>) : InputValue {
        @Serializable
        data class Field(
            val parameter: String,
            val value: InputValue,
        )
    }
}

fun InputValue?.toScriptValue(): ScriptValue {
    return when(this) {
        is InputValue.Primitive -> primitive.toScriptValue()
        is InputValue.Collection -> { SList(elements.map { it.toScriptValue() }) }
        is InputValue.Map -> {
            STable(
                entries.associate { entry ->
                    entry.key.toScriptValue() to entry.value.toScriptValue()
                }
            )
        }
        is InputValue.Record -> {
            STable(
                fields.associate { field ->
                    SString(field.parameter) to field.value.toScriptValue()
                }
            )
        }
        null -> SNil
    }
}

fun InputValue.toJvmValue(expectedType: KType): Any {
    val converted = when(this) {
        is InputValue.Primitive -> {
            primitive.toJvmValue(expectedType.unwrapValueClassType())
        }
        is InputValue.Record -> {
            val kclass = expectedType.classifier as? KClass<*>
                ?: error("$expectedType is not a concrete class")
            val constructor = kclass.primaryConstructor
                ?: error("$kclass has no primary constructor")
            constructor.callBy(
                fields.associate { field ->
                    val property = constructor.parameters.first { it.name == field.parameter }
                    property to field.value.toJvmValue(property.type)
                }
            )
        }
        is InputValue.Collection -> {
            val elementType = expectedType.collectionTypeArgumentOrNull()
                ?: error("$expectedType is not a collection with a concrete element type")
            val values = elements.map {
                it.toJvmValue(elementType)
            }
            when (expectedType.classifier) {
                MutableList::class,
                MutableCollection::class -> values.toMutableList()
                List::class,
                Collection::class -> values.toList()
                else -> values
            }
        }
        is InputValue.Map -> {
            val typeArguments = expectedType.mapTypeArgumentsOrNull()
                ?: error("$expectedType is not a map")
            val keyType = typeArguments.first
                ?: error("$expectedType does not have a concrete key type")
            val valueType = typeArguments.second
                ?: error("$expectedType does not have a concrete value type")
            val entriesJvm = entries.associate { entry ->
                entry.key.toJvmValue(keyType) to entry.value.toJvmValue(valueType)
            }
            when (expectedType.classifier) {
                MutableMap::class -> entriesJvm.toMutableMap()
                Map::class -> entriesJvm.toMap()
                else -> entriesJvm
            }
        }
    }
    return converted.wrapValueClasses(expectedType)
        ?: error("Input value unexpectedly converted to null for $expectedType")
}
