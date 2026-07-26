package org.lain.engine.script.lua

import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.*
import org.lain.engine.util.component.EntityId
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue

class LuaScript<C : ScriptContext, R : Any>(private val luaContext: LuaContext, private val luaFunction: LuaFunction) : Script<C, R> {
    override fun toString(): String {
        return luaFunction.toString()
    }

    override fun execute(context: C): ExecutionResult<R> = with(luaContext) {
        val arguments = luaContext.mapScriptContext(context)
        return try {
            val result = luaFunction.invoke(arguments).arg1().toKotlin()
            ExecutionResult.Success(
                (result ?: Unit) as R
            )
        } catch (e: LuaError) {
            handleScriptException(this@LuaScript, e)
            ExecutionResult.Failure(e)
        }
    }
}

fun LuaValue.toKotlin(): Any? {
    return when (type()) {
        LuaValue.TNIL -> null
        LuaValue.TBOOLEAN -> toboolean()
        LuaValue.TINT -> toint()
        LuaValue.TSTRING -> tojstring()
        LuaValue.TFUNCTION -> { checkfunction().call() }
        LuaValue.TTABLE -> { checktable().toMap { it.toKotlin() } }
        else -> error("Invalid type: " + type())
    }
}

context(writeComponentAccess: WriteComponentAccess)
fun EntityId.setScriptComponent(value: LuaValue, type: ScriptComponentType): ScriptComponent {
    val component = ScriptComponent(value, type)
    setComponent(component, type)
    return component
}