package org.lain.engine.script.lua

import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.player.*
import org.lain.engine.script.BuiltinScriptComponents
import org.lain.engine.script.LOGGER
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

context(ctx: LuaContext)
fun Globals.setupPlayer() {
    ctx.playerTable.set("_is_game_master", object : OneArgFunction() {
        override fun call(self: LuaValue): LuaValue {
            val player = self.coerceToEnginePlayer()
            return luaValue(player.isInGameMasterMode)
        }
    })

    ctx.playerTable.set("_set_custom_max_speed", object : TwoArgFunction() {
        override fun call(self: LuaValue, arg2: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            val speed = arg2.tofloat()
            player.setCustomMaxSpeed(speed)
            return NIL
        }
    })

    ctx.playerTable.set("_reset_custom_max_speed", object : OneArgFunction() {
        override fun call(self: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            player.resetCustomMaxSpeed()
            return NIL
        }
    })

    ctx.playerTable.set("_narration", object : TwoArgFunction() {
        override fun call(self: LuaValue, arg2: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            val narration = arg2.checktable()
            player.serverNarration(
                narration.get("message").tojstring(),
                narration.get("time").toint(),
                narration.get("kick").nullable()?.toboolean() ?: false,
            )
            return NIL
        }
    })
}

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
    set("_remember", object : ThreeArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
            return ctx.dataStorage.getOrDefault(arg3.tojstring(), arg2.tojstring(), arg1)
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
}

context(ctx: LuaContext)
private fun Globals.setupComponentCommands() {
    set("_get_registry_component", object : OneArgFunction() {
        override fun call(arg1: LuaValue): LuaValue {
            val id = arg1.tojstring()
            val components = BuiltinScriptComponents.ALL
            val type = components[id] ?: error("Component type $id not exists. Available: ${components.values}")
            return CoerceJavaToLua.coerce(type)
        }
    })
    set("_register_component", object : OneArgFunction() {
        override fun call(arg1: LuaValue): LuaValue {
            val id = arg1.tojstring()
            val type = ScriptComponentType(ComponentType(id))
            return CoerceJavaToLua.coerce(type)
        }
    })
    set("_get_component", object : ThreeArgFunction() {
        override fun call(
            arg1: LuaValue,
            arg2: LuaValue,
            arg3: LuaValue
        ): LuaValue {
            val entityId = arg2.toint()
            val componentType = arg3.coerceToScriptComponent()
            val world = ctx.getWorld(arg1)
            return world.getLuaComponent(entityId, componentType) ?: NIL
        }
    })
    set("_remove_component", object : ThreeArgFunction() {
        override fun call(
            arg1: LuaValue,
            arg2: LuaValue,
            arg3: LuaValue
        ): LuaValue {
            val entityId = arg2.toint()
            val componentType = arg3.coerceToScriptComponent()
            val world = ctx.getWorld(arg1)
            return world.removeLuaComponent(entityId, componentType) ?: NIL
        }
    })
    set("_set_component", object : VarArgFunction() {
        override fun onInvoke(args: Varargs): Varargs {
            val entityId = args.arg(2).toint()
            val world = ctx.getWorld(args.arg(1))
            val componentType = args.arg(3).coerceToScriptComponent()
            val component = args.arg(4).checktable()
            world.setLuaComponent(entityId, componentType, component)
            return NIL
        }
    })
    set("_iterate_world", object : VarArgFunction() {
        override fun onInvoke(args: Varargs): Varargs {
            val world = ctx.getWorld(args.arg(1))
            val func = args.arg(2).checkfunction()
            val types = setOf(args.arg(3), args.arg(4), args.arg(5), args.arg(6), args.arg(7))
                .filter { !it.isnil() }
            when(types.size) {
                1 -> world.iterate1(types[0].coerceToScriptComponent().ecsType) { entity, component ->
                    func.invoke(luaValue(entity), component.luaTable)
                }
                2 -> world.iterate2(
                    types[0].coerceToScriptComponent().ecsType,
                    types[1].coerceToScriptComponent().ecsType
                ) { entity, component1, component2 ->
                    func.invoke(luaValue(entity), component1.luaTable, component2.luaTable)
                }
                3 -> world.iterate3(
                    types[0].coerceToScriptComponent().ecsType,
                    types[1].coerceToScriptComponent().ecsType,
                    types[2].coerceToScriptComponent().ecsType
                ) { entity, component1, component2, component3 ->
                    func.invoke(
                        arrayOf(luaValue(entity), component1.luaTable, component2.luaTable, component3.luaTable)
                    )
                }
                4 -> world.iterate4(
                    types[0].coerceToScriptComponent().ecsType,
                    types[1].coerceToScriptComponent().ecsType,
                    types[2].coerceToScriptComponent().ecsType,
                    types[3].coerceToScriptComponent().ecsType
                ) { entity, component1, component2, component3, component4 ->
                    func.invoke(
                        arrayOf(luaValue(entity), component1.luaTable, component2.luaTable, component3.luaTable, component4.luaTable)
                    )
                }
                5 -> world.iterate5(
                    types[0].coerceToScriptComponent().ecsType,
                    types[1].coerceToScriptComponent().ecsType,
                    types[2].coerceToScriptComponent().ecsType,
                    types[3].coerceToScriptComponent().ecsType,
                    types[4].coerceToScriptComponent().ecsType
                ) { entity, component1, component2, component3, component4, component5 ->
                    func.invoke(
                        arrayOf(luaValue(entity), component1.luaTable, component2.luaTable, component3.luaTable, component4.luaTable, component5.luaTable)
                    )
                }
            }

            return NIL
        }
    })
}

private fun LuaContext.getPlayer(playerIdArg: LuaValue): EnginePlayer {
    val playerId = PlayerId.fromString(playerIdArg.tojstring())
    return playerStorage.get(playerId) ?: error("Player $playerId not found")
}

private fun LuaContext.getWorld(worldIdArg: LuaValue): World {
    val worldId = WorldId(worldIdArg.tojstring())
    return worlds[worldId] ?: error("World not found")
}

val ScriptComponent.luaTable
    get() = field as? LuaTable ?: error("Component not supports lua")

private fun World.getLuaComponent(entityId: EntityId, componentType: ScriptComponentType): LuaTable? {
    val component = getComponent(entityId, componentType.ecsType) ?: return null
    return (component.field as? LuaTable) ?: error("Component ${componentType.id} not supports lua")
}

private fun World.removeLuaComponent(entityId: EntityId, componentType: ScriptComponentType): LuaTable? {
    val component = removeComponent(entityId, componentType.ecsType) ?: return null
    if (component.field !is LuaTable) error("Removed non-lua component with type ${componentType.id}")
    return component.field
}

private fun World.setLuaComponent(entityId: EntityId, componentType: ScriptComponentType, value: LuaTable) {
    setComponentWithType(entityId, LuaScriptComponent(value), componentType.ecsType)
}