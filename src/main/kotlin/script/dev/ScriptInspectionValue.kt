package org.lain.engine.script.dev

import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive

interface ScriptInspectionValue {
    val identity: Any
        get() = this

    interface Primitive : ScriptInspectionValue {
        val value: ScriptValuePrimitive
    }

    interface Table : ScriptInspectionValue {
        val entries: kotlin.collections.List<Entry>

        data class Entry(
            val key: ScriptInspectionValue,
            val value: ScriptInspectionValue
        )

        fun replace(entry: Entry, value: ScriptValue)
        fun set(key: ScriptValue, value: ScriptValue)
    }

    interface List : ScriptInspectionValue {
        val values: kotlin.collections.List<ScriptInspectionValue>

        fun set(index: Int, value: ScriptValue)
        fun add(value: ScriptValue)
    }

    interface Jvm : ScriptInspectionValue {
        val value: Any
        override val identity: Any
            get() = value
    }
}
