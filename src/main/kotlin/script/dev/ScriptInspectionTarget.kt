package org.lain.engine.script.dev

import org.lain.engine.script.ScriptValue

interface ScriptInspectionTarget {
    val identity: Any
        get() = this

    fun child(key: ScriptValue): ScriptInspectionTarget? = null
    fun indexedChild(idx: Int): ScriptInspectionTarget? = null

    fun set(property: String, value: ScriptValue)
}
