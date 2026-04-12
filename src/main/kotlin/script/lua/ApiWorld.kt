package org.lain.engine.script.lua

import org.lain.engine.script.BuiltinScriptComponents
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.setScriptComponent
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import org.lain.engine.world.setDynamicVoxel
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.lib.TwoArgFunction

context(ctx: LuaContext)
fun World.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> luaValue(this@coerceToLua.id.toString())
                        "is_client" -> luaValue(self.coerceToEngineWorld().isClient)
                        else -> ctx.worldTable.get(key)
                    }
                }
            })
        }
    }
    userdata.setmetatable(meta)
    return userdata
}

fun LuaValue.coerceToEngineWorld() = this.checkuserdata() as World

context(ctx: LuaContext)
fun Globals.setupWorld() {
    ctx.worldTable.set("_add_entity", oneArgFunction { self ->
        val world = self.coerceToEngineWorld()
        luaValue(world.addEntity())
    })
    ctx.worldTable.set("_get_component", threeArgFunction { self, entityId, type ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        luaValue(world.hasLuaComponent(entityId, type))
    })

    ctx.worldTable.set("_has_component", threeArgFunction { self, entityId, type ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        luaValue(world.hasLuaComponent(entityId, type))
    })

    ctx.worldTable.set("_set_component", fourArgFunction { self, entityId, type, component ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        val component = component.checktable()
        world.setLuaComponent(entityId, type, component)
        NIL
    })

    ctx.worldTable.set("_remove_component", threeArgFunction { self, entityId, type ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        world.removeLuaComponent(entityId, type) ?: NIL
    })

    ctx.worldTable.set("_get_all_components", twoArgFunction { self, entityId ->
        val world = self.coerceToEngineWorld()
        LuaTable.listOf(
            world.getComponents(entityId.toint())
                .filterIsInstance<ScriptComponent>()
                .filter { it.field is LuaTable }
                .map { it.luaTable }
                .toTypedArray()
        )
    })

    ctx.worldTable.set("_destroy_entity", twoArgFunction { self, entityId ->
        val world = self.coerceToEngineWorld()
        world.destroy(entityId.toint())
        NIL
    })

    ctx.worldTable.set("_iterate", varargsFunction { args ->
        val world = args.arg(1).coerceToEngineWorld()
        val func = args.arg(2).checkfunction()
        val types = setOf(args.arg(3), args.arg(4), args.arg(5), args.arg(6), args.arg(7))
            .filter { !it.isnil() }
        when (types.size) {
            1 -> world.iterate1(types[0].coerceToScriptComponentType().ecsType) { entity, component ->
                func.invoke(luaValue(entity), component.luaTable)
            }

            2 -> world.iterate2(
                types[0].coerceToScriptComponentType().ecsType,
                types[1].coerceToScriptComponentType().ecsType
            ) { entity, component1, component2 ->
                func.invoke(luaValue(entity), component1.luaTable, component2.luaTable)
            }

            3 -> world.iterate3(
                types[0].coerceToScriptComponentType().ecsType,
                types[1].coerceToScriptComponentType().ecsType,
                types[2].coerceToScriptComponentType().ecsType
            ) { entity, component1, component2, component3 ->
                func.invoke(
                    arrayOf(luaValue(entity), component1.luaTable, component2.luaTable, component3.luaTable)
                )
            }

            4 -> world.iterate4(
                types[0].coerceToScriptComponentType().ecsType,
                types[1].coerceToScriptComponentType().ecsType,
                types[2].coerceToScriptComponentType().ecsType,
                types[3].coerceToScriptComponentType().ecsType
            ) { entity, component1, component2, component3, component4 ->
                func.invoke(
                    arrayOf(
                        luaValue(entity),
                        component1.luaTable,
                        component2.luaTable,
                        component3.luaTable,
                        component4.luaTable
                    )
                )
            }

            5 -> world.iterate5(
                types[0].coerceToScriptComponentType().ecsType,
                types[1].coerceToScriptComponentType().ecsType,
                types[2].coerceToScriptComponentType().ecsType,
                types[3].coerceToScriptComponentType().ecsType,
                types[4].coerceToScriptComponentType().ecsType
            ) { entity, component1, component2, component3, component4, component5 ->
                func.invoke(
                    arrayOf(
                        luaValue(entity),
                        component1.luaTable,
                        component2.luaTable,
                        component3.luaTable,
                        component4.luaTable,
                        component5.luaTable
                    )
                )
            }
        }
        NIL
    })
    ctx.worldTable.set("_set_dynamic_voxel", twoArgFunction { self, pos ->
        val world = self.coerceToEngineWorld()
        val voxelPos = pos.toVoxelPos()
        with(world) {
            val entity = setDynamicVoxel(voxelPos)
            entity.setScriptComponent(
                LuaScriptComponent(voxelPos.toLuaValue()),
                BuiltinScriptComponents.DYNAMIC_VOXEL
            )
            luaValue(entity)
        }
    })
}

val ScriptComponent.luaTable
    get() = field as? LuaValue ?: error("Component not supports lua")

private fun World.hasLuaComponent(entityId: EntityId, componentType: ScriptComponentType): Boolean {
    return hasComponent(entityId, componentType.ecsType)
}

private fun World.getLuaComponent(entityId: EntityId, componentType: ScriptComponentType): LuaValue? {
    val component = getComponent(entityId, componentType.ecsType) ?: return null
    return (component.field as? LuaValue) ?: error("Component ${componentType.id} not supports lua")
}

private fun World.removeLuaComponent(entityId: EntityId, componentType: ScriptComponentType): LuaValue? {
    val component = removeComponent(entityId, componentType.ecsType) ?: return null
    if (component.field !is LuaValue) error("Removed non-lua component with type ${componentType.id}")
    return component.field
}

private fun World.setLuaComponent(entityId: EntityId, componentType: ScriptComponentType, value: LuaValue) {
    setComponentWithType(entityId, LuaScriptComponent(value), componentType.ecsType)
}