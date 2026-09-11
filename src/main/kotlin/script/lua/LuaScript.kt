package org.lain.engine.script.lua

import org.lain.engine.script.*
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.luaWorld
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue

class LuaScript<C : ScriptContext, R : ScriptValue>(private val luaScriptEngine: LuaScriptEngine, private val luaFunction: LuaFunction) : Script<C, R> {
    override fun toString(): String {
        return luaFunction.toString()
    }

    override fun execute(context: C): ExecutionResult<R> = with(luaScriptEngine) {
        val arguments = when(context) {
            is ScriptContext.SystemEntityHandle -> with(context.world) {
                val array = Array<LuaValue?>(context.components.size + 2) { null }
                array[0] = context.world.luaWorld()
                array[1] = context.entity.luaEntity()
                context.components.forEachIndexed { index, component ->
                    array[2 + index] = component.castLua().luaValue
                }
                array
            }
            else -> arrayOf(luaScriptEngine.mapScriptContext(context))
        }
        return try {
            val result = luaFunction.invoke(arguments).arg1().toScriptValue()
            ExecutionResult.Success(
                (result ?: Unit) as R
            )
        } catch (e: LuaError) {
            handleScriptException(this@LuaScript, e)
            ExecutionResult.Failure(e)
        }
    }
}