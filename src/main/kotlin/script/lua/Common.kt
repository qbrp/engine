package org.lain.engine.script.lua

import org.lain.engine.Constants
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.script.*
import org.lain.engine.util.Environment
import org.lain.engine.util.file.BUILTIN_SCRIPTS_DIR
import org.lain.engine.util.inject
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

fun debugScript(module: String, info: String) {
    if (Constants.DEBUG_ALL) {
        LOGGER.info("[$module] $info")
    }
}

fun LogTable() = luaTable {
    function1("info") { str ->
        LOGGER.info(str.tojstring())
        LuaValue.NIL
    }
    function2("debug") { module, str ->
        debugScript(module.tojstring(), str.tojstring())
        LuaValue.NIL
    }
}

context(ctx: LuaContext)
fun Globals.setup() {
    set("SCRIPTS_PATH", ctx.scriptsPath)
    set("LIBRARY_PATH", BUILTIN_SCRIPTS_DIR.path)
    set("remember", object : ThreeArgFunction() {
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
    set("is_client", object : ZeroArgFunction() {
        override fun call(): LuaValue {
            val env by inject<Environment>()
            return luaValue(env == Environment.CLIENT)
        }
    })
}

private fun LuaContext.getPlayer(playerIdArg: LuaValue): EnginePlayer {
    val playerId = PlayerId.fromString(playerIdArg.tojstring())
    return runtimeDependencies?.playerStorage?.get(playerId) ?: error("Player $playerId not found")
}

internal fun LuaContext.getWorld(worldIdArg: LuaValue): World {
    val worldId = WorldId(worldIdArg.tojstring())
    return runtimeDependencies?.worlds[worldId] ?: error("World not found")
}
