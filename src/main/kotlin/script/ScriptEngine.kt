package org.lain.engine.script

interface ScriptEngine {
    fun createScriptComponent(value: ScriptValue, type: ScriptComponentType): ScriptComponent
    fun reloadScript(filename: String)

    object Dummy : ScriptEngine {
        override fun createScriptComponent(
            value: ScriptValue,
            type: ScriptComponentType
        ): ScriptComponent {
            throw NotImplementedError()
        }

        override fun reloadScript(filename: String) {}
    }
}