package org.lain.engine.script.dev

import org.lain.engine.script.SList
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive

interface ScriptInspectionValue {
    val identity: Any
        get() = this

    interface Primitive : ScriptInspectionValue {
        val value: ScriptValuePrimitive
    }

    interface Table : ScriptInspectionValue {
        val value: STable

        fun child(key: ScriptValue): ScriptInspectionValue
        fun set(key: ScriptValue, value: ScriptValue)
    }

    interface List : ScriptInspectionValue {
        val value: SList

        fun child(index: Int): ScriptInspectionValue
        fun set(index: Int, value: ScriptValue)
    }

    interface Jvm : ScriptInspectionValue {
        val value: Any
        override val identity: Any
            get() = value
    }
}