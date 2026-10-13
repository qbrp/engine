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
    return when(this) {
        is InputValue.Primitive -> {
            val expectedClass = expectedType.classifier as? KClass<*>
            if (expectedClass?.java?.isEnum == true) {
                val name = (primitive as? InspectionPrimitive.Str)?.string
                    ?: error("Enum value must be a string")

                expectedClass.java.enumConstants
                    .map { it as kotlin.Enum<*> }
                    .firstOrNull { it.name == name }
                    ?: error("Enum constant $name not found in ${expectedClass.qualifiedName}")
            } else {
                primitive.toJvmValue()
            }
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
            val listType = expectedType.listTypeArgument()
            val values = elements.map {
                it.toJvmValue(listType)
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
            val (keyType, valueType) = expectedType.mapTypeArguments()
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
}
