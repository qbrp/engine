package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.script.EngineId
import org.lain.engine.script.SBool
import org.lain.engine.script.SEntityRef
import org.lain.engine.script.SId
import org.lain.engine.script.SInstant
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptValue
import org.lain.engine.data.Uuid as EngineUuid
import java.util.UUID
import javax.naming.OperationNotSupportedException
import kotlin.reflect.KType


@Serializable
@SerialName("primitive")
sealed class InspectionPrimitive {
    abstract fun toScriptValue(): ScriptValue
    abstract fun toJvmValue(kType: KType?): Any

    @Serializable
    @SerialName("str")
    data class Str(val string: String) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue = SString(string)
        override fun toJvmValue(kType: KType?): Any = string
    }

    @Serializable
    @SerialName("enum")
    data class Enum(val name: String) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue =
            SString(name)

        override fun toJvmValue(kType: KType?): Any {
            val expectedClass = kType?.propertyClassifier
                ?: error("Enum KType is required")
            require(expectedClass.java.isEnum) { "$expectedClass is not an enum" }
            return expectedClass.java.enumConstants
                .map { it as kotlin.Enum<*> }
                .firstOrNull { it.name == name }
                ?: error("Enum constant $name does not exist in $expectedClass")
        }
    }

    @Serializable
    @SerialName("char")
    data class Char(val char: kotlin.Char) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue = SString(char.toString())
        override fun toJvmValue(kType: KType?): Any = char
    }

    @Serializable
    @SerialName("bool")
    data class Bool(val bool: Boolean) : InspectionPrimitive() {
        val string = bool.toString()
        override fun toScriptValue(): ScriptValue = SBool(bool)
        override fun toJvmValue(kType: KType?): Any = bool
    }

    @Serializable
    @SerialName("int")
    data class Int(val int: kotlin.Int) : InspectionPrimitive() {
        val string = int.toString()
        override fun toScriptValue(): ScriptValue = SNumber(int.toDouble())
        override fun toJvmValue(kType: KType?): Any = int
    }

    @Serializable
    @SerialName("double")
    data class Double(val double: kotlin.Double) : InspectionPrimitive() {
        val string = double.toString()
        override fun toScriptValue(): ScriptValue = SNumber(double)
        override fun toJvmValue(kType: KType?): Any = double
    }

    @Serializable
    @SerialName("uuid")
    data class Uuid(val uuid: String) : InspectionPrimitive() {
        val string = uuid
        override fun toScriptValue(): ScriptValue = SString(uuid)
        override fun toJvmValue(kType: KType?): Any {
            return when (kType?.classifier) {
                EngineUuid::class -> EngineUuid.from(uuid)
                else -> UUID.fromString(uuid)
            }
        }
    }

    @Serializable
    @SerialName("engine_id")
    data class Id(val id: EngineId) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue = SId(id)
        override fun toJvmValue(kType: KType?): Any = id
    }

    @Serializable
    @SerialName("entity_ref")
    data class EntityRef(val entityId: kotlin.Int) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue {
            return SEntityRef(entityId)
        }
        override fun toJvmValue(kType: KType?): Any = EntityRef(entityId)
    }

    @Serializable
    @SerialName("instant")
    data class Instant(val epochMillis: Long) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue {
            return SInstant(instant())
        }
        override fun toJvmValue(kType: KType?): Any {
            return instant()
        }

        private fun instant() = java.time.Instant.ofEpochMilli(epochMillis)
    }


    @Serializable
    @SerialName("other")
    data class Other(val str: String) : InspectionPrimitive() {
        override fun toJvmValue(kType: KType?): Any {
            throw OperationNotSupportedException()
        }

        override fun toScriptValue(): ScriptValue {
            throw OperationNotSupportedException()
        }

        val string = str
    } // non editable
}
