package org.lain.engine.script.lua.library

import org.lain.engine.script.CallbackType
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.compilation.runCompilationFunctionsLua
import org.lain.engine.script.lua.luaTable
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue.NIL

class RegistrationLibrary {
    private val compilationFunctions = mutableListOf<LuaFunction>()

    val registrationTable = luaTable {
        function1("on_compilation") { function ->
            compilationFunctions.add(function.checkfunction())
            NIL
        }
    }

    context(lua: LuaScriptEngine)
    fun runFunctions(callbackTypes: List<CallbackType<*>>) = runCompilationFunctionsLua(
        compilationFunctions,
        callbackTypes
    )

    fun setup(globals: Globals) {
        globals["Registration"] = registrationTable
    }
}