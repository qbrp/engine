package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.deepCopy

@Serializable
sealed interface ScriptValue {
    fun copy(): ScriptValue
}

@Serializable
data class SNumber(val value: Double) : ScriptValue {
    override fun copy(): SNumber = SNumber(value)
}

@Serializable
data class SInt(val value: Int) : ScriptValue {
    override fun copy(): SInt = SInt(value)
}

@Serializable
data class SString(val value: String) : ScriptValue {
    override fun copy(): SString = SString(value)
}

@Serializable
data class SBool(val value: Boolean) : ScriptValue {
    override fun copy(): SBool = SBool(value)
}

data class SEntityRef(val id: EntityId) : ScriptValue {
    override fun copy(): ScriptValue {
        return SEntityRef(id)
    }
}

@Serializable
data class STable(val map: Map<ScriptValue, ScriptValue>) : ScriptValue {
    override fun copy(): STable = STable(
        map.deepCopy(
            { it.copy() },
            { it.copy()}
        )
    )
}

@Serializable
data class SList(val values: List<ScriptValue>) : ScriptValue {
    override fun copy(): SList = SList(values.map { it.copy() })
}

@Serializable
object SNil : ScriptValue {
    override fun copy(): ScriptValue = SNil
}