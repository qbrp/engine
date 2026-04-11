package org.lain.engine.script.lua

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Player
import org.lain.engine.script.*
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua

class LuaScript<C : ScriptContext, R : Any>(private val luaContext: LuaContext, private val luaFunction: LuaFunction) : Script<C, R> {
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
        }
        return try {
            val result = luaFunction.invoke(arguments).arg1().toKotlin()
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

fun LuaScriptComponent(value: LuaValue) = ScriptComponent(value)

fun LuaUserdataComponent(component: Component) = LuaScriptComponent(CoerceJavaToLua.coerce(component))

context(world: World, luaContext: LuaContext)
fun EnginePlayer.prepareLuaScriptComponents() {
    entityId.setScriptComponent(
        LuaScriptComponent(luaTableOf(luaValue("object"), coerceToLua())),
        BuiltinScriptComponents.PLAYER
    )
    entityId.setScriptComponent(
        LuaScriptComponent(
            luaTableOf(luaValue("vector"), LuaValue.NIL),
        ),
        BuiltinScriptComponents.LOCATION
    )
}

fun updateScriptComponents(world: World) {
    world.iterate<Player, Location>() { player, _, location ->
        val scriptLocation = player.requireComponent(BuiltinScriptComponents.LOCATION.ecsType)
        scriptLocation.luaTable.set("vector", location.position.toLuaValue())
    }
}