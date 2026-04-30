package org.lain.engine.script.lua

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
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
    val idValue = luaValue(this@coerceToLua.id.toString())
    val isClient = luaValue(isClient)
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> idValue
                        "is_client" -> isClient
                        "players" -> LuaTable.listOf(players.map { it.coerceToLua() }.toTypedArray())
                        else -> ctx.worldTable.get(key)
                    }
                }
            })
        }
    }
    userdata.setmetatable(meta)
    return userdata
}

data class LuaEntityComponent(val table: LuaValue) : Component {
    companion object {
        val TYPE by lazy { componentTypeOf(LuaEntityComponent::class) }
    }
}

context(ctx: LuaContext, world: World)
fun EntityId.coerceToLua(): LuaValue {
    val idValue = luaValue(this)
    val worldValue = world.coerceToLua()
    return getComponent<LuaEntityComponent>()?.table ?: run {
        val t = object : LuaTable() {
            override fun get(key: LuaValue): LuaValue {
                return when (key.tojstring()) {
                    "id" -> idValue
                    "world" -> worldValue
                    else -> ctx.entityTable.get(key)
                }
            }
        }
        setComponent<LuaEntityComponent>(LuaEntityComponent(t))
        t
    }
}

fun LuaValue.coerceToEngineWorld() = this.checkuserdata() as World

context(ctx: LuaContext)
fun Globals.setupEntity() {
    ctx.entityTable.set("_of", twoArgFunction { world, id ->
        val world = world.coerceToEngineWorld()
        with(world) { id.toint().coerceToLua() }
    })
}

context(ctx: LuaContext)
fun Globals.setupWorld() {
    ctx.worldTable.set("_add_entity", oneArgFunction { self ->
        val world = self.coerceToEngineWorld()
        with(world) { world.addEntity().coerceToLua() }
    })
    ctx.worldTable.set("_get_component", threeArgFunction { self, entityId, type ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        luaValue(world.hasLuaComponent(entityId, type.requireComponent()))
    })

    ctx.worldTable.set("_has_component", threeArgFunction { self, entityId, type ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        luaValue(world.hasLuaComponent(entityId, type.requireComponent()))
    })

    ctx.worldTable.set("_set_component", fourArgFunction { self, entityId, type, component ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        val component = component.checktable()
        with(world) { entityId.setLuaComponent(component, type.requireComponent()) }
        NIL
    })

    ctx.worldTable.set("_remove_component", threeArgFunction { self, entityId, type ->
        val world = self.coerceToEngineWorld()
        val type = type.coerceToScriptComponentType()
        val entityId = entityId.toint()
        world.removeLuaComponent(entityId, type.requireComponent()) ?: NIL
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
        val entityArray = world.componentManager.getComponentArray(LuaEntityComponent.TYPE)

        fun getOrCreateEntityComponent(entityId: EntityId): LuaEntityComponent {
            return entityArray.componentOf(entityId) ?: run {
                with(world) { entityId.coerceToLua() }
                entityArray.componentOf(entityId)!!
            }
        }

        when (types.size) {
            1 -> world.iterate1(types[0].coerceToScriptComponentType().ecsType) { entity, component ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    luaEntity.table,
                    component.luaTable
                )
            }

            2 -> world.iterate2(
                types[0].coerceToScriptComponentType().ecsType,
                types[1].coerceToScriptComponentType().ecsType
            ) { entity, component1, component2 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(luaEntity.table, component1.luaTable, component2.luaTable)
            }

            3 -> world.iterate3(
                types[0].coerceToScriptComponentType().ecsType,
                types[1].coerceToScriptComponentType().ecsType,
                types[2].coerceToScriptComponentType().ecsType
            ) { entity, component1, component2, component3 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(luaEntity.table, component1.luaTable, component2.luaTable, component3.luaTable)
                )
            }

            4 -> world.iterate4(
                types[0].coerceToScriptComponentType().ecsType,
                types[1].coerceToScriptComponentType().ecsType,
                types[2].coerceToScriptComponentType().ecsType,
                types[3].coerceToScriptComponentType().ecsType
            ) { entity, component1, component2, component3, component4 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        luaEntity.table,
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
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        luaEntity.table,
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
    ctx.worldTable.set("_set_dynamic_voxel", threeArgFunction { self, pos, networked ->
        val world = self.coerceToEngineWorld()
        val voxelPos = pos.toVoxelPos()
        with(world) {
            val entity = setDynamicVoxel(voxelPos, networked.toboolean())
            entity.setLuaComponent(
                voxelPos.toLuaValue(),
                CoreScriptComponents.DYNAMIC_VOXEL
            )
            entity.coerceToLua()
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