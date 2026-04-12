package org.lain.engine.script.lua

import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.SharedConstants
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.script.BuiltinScriptComponents
import org.lain.engine.script.LOGGER
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.util.Environment
import org.lain.engine.util.inject
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

context(ctx: LuaContext)
fun Globals.setup() {
    setupComponentCommands()
    set("SCRIPTS_PATH", ctx.scriptsPath.path)
    set("_info", object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            LOGGER.info(arg.tojstring())
            return NIL
        }
    })
    set("_debug", object : TwoArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
            if (SharedConstants.DEBUG_ALL) {
                LOGGER.info("[${arg1.tojstring()}] ${arg2.tojstring()}")
            }
            return NIL
        }
    })
    set("_remember", object : ThreeArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
            return ctx.dependencies.dataStorage.getOrDefault(arg3.tojstring(), arg2.tojstring(), arg1)
        }
    })
    set("_compilation", object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue? {
            ctx.compilationFunctions += arg.checkfunction()
            return NIL
        }
    })
    set("_callbacks", object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue? {
            ctx.callbacksFunctions.add(arg.checkfunction())
            return NIL
        }
    })
    set("_is_client", object : ZeroArgFunction() {
        override fun call(): LuaValue {
            val env by inject<Environment>()
            return luaValue(env == Environment.CLIENT)
        }
    })
}

context(ctx: LuaContext)
private fun Globals.setupComponentCommands() {
    set("_get_builtin_component", object : OneArgFunction() {
        override fun call(arg1: LuaValue): LuaValue {
            val id = arg1.tojstring()
            val components = BuiltinScriptComponents.ALL
            val type = components[id] ?: kotlin.error("Builtin component type $id not exists. Available: ${components.values.map { it.id }}")
            return type.toLuaValue()
        }
    })
    set("_register_component", object : OneArgFunction() {
        override fun call(arg1: LuaValue): LuaValue {
            val id = arg1.tojstring()
            val type = ScriptComponentType(ComponentType(id))
            return type.toLuaValue()
        }
    })
}

private fun LuaContext.getPlayer(playerIdArg: LuaValue): EnginePlayer {
    val playerId = PlayerId.fromString(playerIdArg.tojstring())
    return dependencies.playerStorage.get(playerId) ?: error("Player $playerId not found")
}

internal fun LuaContext.getWorld(worldIdArg: LuaValue): World {
    val worldId = WorldId(worldIdArg.tojstring())
    return dependencies.worlds[worldId] ?: error("World not found")
}
