package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.engine.util.component.EntityId

@Serializable
sealed interface ScriptValue
@Serializable
data class SNumber(val value: Double) : ScriptValue
@Serializable
data class SInt(val value: Int) : ScriptValue
@Serializable
data class SString(val value: String) : ScriptValue
@Serializable
data class SBool(val value: Boolean) : ScriptValue
@Serializable
data class STable(val map: Map<ScriptValue, ScriptValue>) : ScriptValue
@Serializable
data class SList(val values: List<ScriptValue>) : ScriptValue
@Serializable
object SNil : ScriptValue