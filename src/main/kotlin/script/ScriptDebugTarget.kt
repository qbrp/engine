package org.lain.engine.script

/**
 * A live script object associated with a debug snapshot.
 *
 * [identity] must refer to the underlying script object so that separate target
 * wrappers around the same object are represented by the same debug reference.
 */
interface ScriptDebugTarget {
    val identity: Any
        get() = this

    fun child(key: ScriptValue): ScriptDebugTarget? = null

    fun set(property: String, value: ScriptValue)
}
