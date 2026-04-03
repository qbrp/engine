package org.lain.engine.script

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue

class LuaScript<C : ScriptContext, R : Any>(private val luaFunction: LuaFunction) : Script<C, R> {
    override fun execute(context: C): ExecutionResult<R> {
        val argument = when(context) {
            is ScriptContext.Player -> {
                LuaValue.valueOf(context.player.id.toString())
            }
            is ScriptContext.World -> {
                LuaValue.valueOf(context.world.id.toString())
            }
        }
        return try {
            val result = luaFunction.call(argument).toKotlin()
            ExecutionResult.Success(
                (result ?: Unit) as R
            )
        } catch (e: LuaError) {
            LOGGER.error("Ошибка выполнения скрипта $luaFunction", e)
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
        LuaValue.TTABLE -> { checktable().toMap {  } }
        else -> error("Invalid type: " + type())
    }
}