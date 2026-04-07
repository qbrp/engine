package org.lain.engine.script.lua

import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.*
import org.lain.engine.world.World
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaScript<C : ScriptContext, R : Any>(private val luaContext: LuaContext, private val luaFunction: LuaFunction) : Script<C, R> {
    override fun execute(context: C): ExecutionResult<R> = with(luaContext) {
        val arguments = when(context) {
            is ScriptContext.Player -> {
                arrayOf(luaValue(context.player.id.toString()))
            }
            is ScriptContext.World -> {
                arrayOf(luaValue(context.world.id.toString()))
            }
            is ScriptContext.Interaction -> {
                arrayOf(
                    context.player.coerceToLua(),
                    context.raycastPlayer?.coerceToLua()
                )
            }
        }
        return try {
            val result = luaFunction.invoke(LuaValue.varargsOf(arguments)).arg1().toKotlin()
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

fun LuaScriptComponent(table: LuaTable) = ScriptComponent(table)

context(world: World, luaContext: LuaContext)
fun EnginePlayer.prepareLuaScriptComponents() {
    entityId.setScriptComponent(
        LuaScriptComponent(luaTableOf(luaValue("object"), coerceToLua())),
        BuiltinScriptComponents.PLAYER
    )
}