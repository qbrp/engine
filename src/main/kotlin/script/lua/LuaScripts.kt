package org.lain.engine.script.lua

import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.*
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.world
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue

class LuaScript<C : ScriptContext, R : Any>(private val luaContext: LuaContext, private val luaFunction: LuaFunction) : Script<C, R> {
    override fun toString(): String {
        return luaFunction.toString()
    }

    override fun execute(context: C): ExecutionResult<R> = with(luaContext) {
        val arguments = when(context) {
            is ScriptContext.Player -> {
                context.player.coerceToLua()
            }
            is ScriptContext.World -> {
                context.world.coerceToLua()
            }
            is ScriptContext.Interaction -> {
                luaTableOf(
                    luaValue("player"), context.player.coerceToLua(),
                    luaValue("raycast_player"), context.raycastPlayer?.coerceToLua() ?: LuaValue.NIL,
                )
            }
            is ScriptContext.VoxelAction -> {
                luaTableOf(
                    luaValue("player"), context.player?.coerceToLua() ?: LuaValue.NIL,
                    luaValue("world"), context.world.coerceToLua(),
                    luaValue("voxel_pos"), context.pos.toLuaValue(),
                    luaValue("voxel_meta"), context.meta.coerceToLua(),
                )
            }
            is ScriptContext.IntentExecution -> {
                val (actor, target, inputs, behaviour) = context
                luaTableOf(
                    luaValue("world"), actor.player.world.coerceToLua(),
                    luaValue("actor"), luaTableOf(
                        luaValue("type"), actor.type.name.lowercase().toLuaValue(),
                        luaValue("player"), actor.player.coerceToLua(),
                        luaValue("entity"), actor.entity.toLuaValue(),
                    ),
                    luaValue("target"), target?.toLuaValue() ?: LuaValue.NIL,
                    luaValue("inputs"), inputs.toLuaTable(),
                    luaValue("gen_target"), zeroArgFunction { behaviour.generateTarget().toLuaValue() },
                    luaValue("gen_selection"), zeroArgFunction { behaviour.generateSelection()?.toLuaValue() ?: LuaValue.NIL },
                    luaValue("feedback"), oneArgFunction {
                        behaviour.feedback(it.tojstring())
                        LuaValue.NIL
                    }
                )
            }
        }
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
fun EntityId.setLuaComponent(value: LuaValue, type: ScriptComponentType) {
    setComponent(ScriptComponent(value, type), type.ecsType)
}