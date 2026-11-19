package org.lain.engine.util.property

import kotlinx.serialization.Serializable
import org.lain.engine.script.SList
import org.lain.engine.script.SNil
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValue
import org.lain.engine.util.collectionTypeArgumentOrNull
import org.lain.engine.util.mapTypeArgumentsOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

@Serializable
sealed interface PropertyInput {
    fun toScriptValue(): ScriptValue
    fun toJvmValue(expectedType: KType): Any?

    @Serializable
    object Null : PropertyInput {
        override fun toScriptValue(): ScriptValue = SNil
        override fun toJvmValue(expectedType: KType): Any? = null
    }

    @Serializable
    data class Scalar(val scalar: PropertyScalar) : PropertyInput {
        override fun toJvmValue(expectedType: KType): Any = scalar.toJvmValue(expectedType)
        override fun toScriptValue(): ScriptValue = scalar.toScriptValue()
    }

    @Serializable
    data class Map(val entries: List<Entry>) : PropertyInput {
        @Serializable
        data class Entry(
            val key: PropertyInput,
            val value: PropertyInput,
        )

        override fun toJvmValue(expectedType: KType): Any {
            val typeArguments = expectedType.mapTypeArgumentsOrNull()
                ?: error("$expectedType is not a map")
            val keyType = typeArguments.first
                ?: error("$expectedType does not have a concrete key type")
            val valueType = typeArguments.second
                ?: error("$expectedType does not have a concrete value type")
            val entriesJvm = entries.associate { entry ->
                entry.key.toJvmValue(keyType) to entry.value.toJvmValue(valueType)
            }
            return when (expectedType.classifier) {
                MutableMap::class -> entriesJvm.toMutableMap()
                kotlin.collections.Map::class -> entriesJvm.toMap()
                else -> entriesJvm
            }
        }

        override fun toScriptValue(): ScriptValue {
            return STable(
                entries.associate { entry ->
                    entry.key.toScriptValue() to entry.value.toScriptValue()
                }
            )
        }
    }

    @Serializable
    data class Collection(val elements: List<PropertyInput>) : PropertyInput {
        override fun toJvmValue(expectedType: KType): Any {
            val elementType = expectedType.collectionTypeArgumentOrNull()
                ?: error("$expectedType is not a collection with a concrete element type")
            val values = elements.map {
                it.toJvmValue(elementType)
            }
            return when (expectedType.classifier) {
                MutableList::class,
                MutableCollection::class -> values.toMutableList()
                List::class,
                kotlin.collections.Collection::class -> values.toList()
                else -> values
            }
        }

        override fun toScriptValue(): ScriptValue {
            return SList(elements.map { it.toScriptValue() })
        }
    }

    @Serializable
    data class Record(val fields: List<Field>) : PropertyInput {
        @Serializable
        data class Field(
            val parameter: String,
            val value: PropertyInput,
        )

        override fun toJvmValue(expectedType: KType): Any {
            val kclass = expectedType.classifier as? KClass<*>
                ?: error("$expectedType is not a concrete class")
            val constructor = kclass.primaryConstructor
                ?: error("$kclass has no primary constructor")
            return constructor.callBy(
                fields.associate { field ->
                    val property = constructor.parameters.first { it.name == field.parameter }
                    property to field.value.toJvmValue(property.type)
                }
            )
        }

        override fun toScriptValue(): ScriptValue {
            return STable(
                fields.associate { field ->
                    SString(field.parameter) to field.value.toScriptValue()
                }
            )
        }
    }
}
