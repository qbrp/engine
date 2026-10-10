package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.script.EngineId
import org.lain.engine.script.SBool
import org.lain.engine.script.SId
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptValue
import java.util.UUID
import javax.naming.OperationNotSupportedException


@Serializable
@SerialName("primitive")
sealed class InspectionPrimitive {
    abstract fun toScriptValue(): ScriptValue
    abstract fun toJvmValue(): Any

    @Serializable
    @SerialName("str")
    data class Str(val string: String) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue = SString(string)
        override fun toJvmValue(): Any = string
    }

    @Serializable
    @SerialName("char")
    data class Char(val char: kotlin.Char) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue = SString(char.toString())
        override fun toJvmValue(): Any = char
    }

    @Serializable
    @SerialName("bool")
    data class Bool(val bool: Boolean) : InspectionPrimitive() {
        val string = bool.toString()
        override fun toScriptValue(): ScriptValue = SBool(bool)
        override fun toJvmValue(): Any = bool
    }

    @Serializable
    @SerialName("int")
    data class Int(val int: kotlin.Int) : InspectionPrimitive() {
        val string = int.toString()
        override fun toScriptValue(): ScriptValue = SNumber(int.toDouble())
        override fun toJvmValue(): Any = int
    }

    @Serializable
    @SerialName("double")
    data class Double(val double: kotlin.Double) : InspectionPrimitive() {
        val string = double.toString()
        override fun toScriptValue(): ScriptValue = SNumber(double)
        override fun toJvmValue(): Any = double
    }

    @Serializable
    @SerialName("uuid")
    data class Uuid(val uuid: String) : InspectionPrimitive() {
        val string = uuid
        override fun toScriptValue(): ScriptValue = SString(uuid)
        override fun toJvmValue(): Any = UUID.fromString(uuid)
    }

    @Serializable
    @SerialName("engine_id")
    data class Id(val id: EngineId) : InspectionPrimitive() {
        override fun toScriptValue(): ScriptValue = SId(id)
        override fun toJvmValue(): Any = id
    }

    @Serializable
    @SerialName("enum")
    data class Enum(val enumClass: String, val name: String) : InspectionPrimitive() {
        val string = name
        override fun toScriptValue(): ScriptValue = SString(name)

        @Suppress("UNCHECKED_CAST")
        override fun toJvmValue(): Any {
            val clazz = Class.forName(enumClass)
            val result = (clazz.enumConstants as Array<kotlin.Enum<*>>)
                .firstOrNull { it.name == name }
            return result!!
        }
    }

    @Serializable
    @SerialName("other")
    data class Other(val str: String) : InspectionPrimitive() {
        override fun toJvmValue(): Any {
            throw OperationNotSupportedException()
        }

        override fun toScriptValue(): ScriptValue {
            throw OperationNotSupportedException()
        }

        val string = str
    } // non editable
}