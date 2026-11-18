package org.lain.engine.util.property

import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive

interface ScriptValueBinding {
    val identity: Any
        get() = this

    interface Primitive : ScriptValueBinding {
        val value: ScriptValuePrimitive
    }

    interface Table : ScriptValueBinding {
        val entries: kotlin.collections.List<Entry>

        data class Entry(
            val key: ScriptValueBinding,
            val value: ScriptValueBinding
        )

        fun replace(entry: Entry, value: ScriptValue)
        fun set(key: ScriptValue, value: ScriptValue)
    }

    interface List : ScriptValueBinding {
        val values: kotlin.collections.List<ScriptValueBinding>

        fun set(index: Int, value: ScriptValue)
        fun add(value: ScriptValue)
    }

    interface Jvm : ScriptValueBinding {
        val value: Any
        override val identity: Any
            get() = value
    }
}
