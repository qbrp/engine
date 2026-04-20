package org.lain.engine.script.lua

import org.lain.engine.SharedConstants
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

context(ctx: LuaContext)
fun Globals.setup() {
    setupComponentCommands()
    set("SCRIPTS_PATH", ctx.scriptsPath.path)
    set("LIBRARY_PATH", BUILTIN_SCRIPTS_DIR.path)
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

class LazyScriptComponentType(
    private val storage: NamespacedStorage,
    val id: ScriptComponentId
) {
    private var componentType: ScriptComponentType? = null
    val ecsType get() = requireComponent().ecsType

    fun getComponent(): ScriptComponentType? {
        return storage.components[id] ?: BuiltinScriptComponents.ALL[id.id]
    }

    fun requireComponent(): ScriptComponentType {
        return componentType ?: (getComponent() ?: error("Component $id not registered in system"))
            .also { componentType = it }
    }
}

context(ctx: LuaContext)
private fun Globals.setupComponentCommands() {
    set("_component_type_of", object : OneArgFunction() {
        override fun call(arg1: LuaValue): LuaValue {
            val id = arg1.tojstring()
            val type = LazyScriptComponentType(ctx.dependencies.namespacesStorage, ScriptComponentId(id))
            return type.toLuaValue()
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
