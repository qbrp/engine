package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.*
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.castLua
import org.lain.engine.script.lua.getLuaScriptComponent
import org.lain.engine.script.lua.hasLuaScriptComponent
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaUserdataTable
import org.lain.engine.script.lua.castLua
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.removeLuaScriptComponent
import org.lain.engine.script.lua.setLuaScriptComponent
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toVoxelPos
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import org.lain.engine.world.invokeCommand
import org.lain.engine.world.setDynamicVoxel
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.lib.jse.CoerceJavaToLua

// World

context(lua: LuaScriptEngine)
fun WorldMetaTable() = luaTable {
    function2("invoke_command") { self, command ->
        val world = self.asEngineWorld()
        val commandL = command.tojstring()
        world.invokeCommand(commandL)
        NIL
    }

    function2("add_entity") { self, components ->
        val world = self.asEngineWorld()
        val components = components.nullable()?.checktable()?.toList {
            it.get("type").asEngineScriptComponentType() to it
        } ?: emptyList()
        val entity = with(world) {
            val e = world.addEntity()
            components.forEach { (type, component) ->
                e.setLuaScriptComponent(component, type.requireType())
            }
            e.coerceToLua()
        }
        debugScript("entity", "($entity) added")
        entity
    }

    function2("destroy_entity") { self, entityId ->
        val world = self.asEngineWorld()
        world.destroy(entityId.toint())
        NIL
    }

    function3("set_dynamic_voxel") { self, pos, networked ->
        val world = self.asEngineWorld()
        val voxelPos = pos.toVoxelPos()
        with(world) {
            val entity = setDynamicVoxel(voxelPos, networked.nullable()?.toboolean() ?: false)
            debugScript(
                "voxel",
                "($entity) created dynamic voxel, networked = $networked"
            )
            entity.coerceToLua()
        }
    }

    function2("get_dynamic_voxel") { self, pos ->
        val world = self.asEngineWorld()
        val voxelPos = pos.toVoxelPos()
        with(world) { chunkStorage.getDynamicVoxel(voxelPos)?.coerceToLua() ?: NIL }
    }

    function3("emit") { self, event, networkedL ->
        val world = self.asEngineWorld()
        val eventType = event.get("type").asEngineScriptComponentType().requireType()
        val networked = networkedL.toboolean()
        with(world) {
            world.emitEvent(
                LuaScriptComponent(event, eventType),
                eventType,
                networked
            ).coerceToLua()
        }
    }

    function3("iterate") { self, types, func ->
        val world = self.asEngineWorld()
        val typesL = types.checktable().toList { it.get("type").asEngineScriptComponentType() }
        val entityArray = world.componentManager.getComponentArray(LuaEntityComponent.TYPE)
        fun getOrCreateEntityComponent(entityId: EntityId): LuaEntityComponent {
            return entityArray.componentOf(entityId) ?: run {
                with(world) { entityId.coerceToLua() }
                entityArray.componentOf(entityId)!!
            }
        }

        when (typesL.size) {
            1 -> world.iterate1(typesL[0].requireType()) { entity, component ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    self,
                    luaEntity.coercedTable,
                    component.castLua().luaValue
                )
            }

            2 -> world.iterate2(
                typesL[0].requireType(),
                typesL[1].requireType()
            ) { entity, component1, component2 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.castLua().luaValue,
                        component2.castLua().luaValue
                    )
                )
            }

            3 -> world.iterate3(
                typesL[0].requireType(),
                typesL[1].requireType(),
                typesL[2].requireType()
            ) { entity, component1, component2, component3 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.castLua().luaValue,
                        component2.castLua().luaValue,
                        component3.castLua().luaValue
                    )
                )
            }

            4 -> world.iterate4(
                typesL[0].requireType(),
                typesL[1].requireType(),
                typesL[2].requireType(),
                typesL[3].requireType()
            ) { entity, component1, component2, component3, component4 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.castLua().luaValue,
                        component2.castLua().luaValue,
                        component3.castLua().luaValue,
                        component4.castLua().luaValue
                    )
                )
            }

            5 -> world.iterate5(
                typesL[0].requireType(),
                typesL[1].requireType(),
                typesL[2].requireType(),
                typesL[3].requireType(),
                typesL[4].requireType()
            ) { entity, component1, component2, component3, component4, component5 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.castLua().luaValue,
                        component2.castLua().luaValue,
                        component3.castLua().luaValue,
                        component4.castLua().luaValue,
                        component5.castLua().luaValue
                    )
                )
            }
        }
        NIL
    }
}

context(lua: LuaScriptEngine)
fun World.coerceToLua(): LuaUserdata {
    val world = this

    val parameters = luaTable {
        "id"(id.value.luaStr())
        "is_client"(isClient.luaBool())
    }

    val userdata = LuaUserdata(world)
    userdata.setmetatable(
        luaTable {
            "parameters"(parameters)
            index { self, key ->
                if (key.tojstring() == "players") {
                    players.toLuaList { it.coerceToLua() }
                } else {
                    parameters.get(key)?.nullable() ?: lua.worldMetaTable.get(key)
                }
            }
        }
    )

    return userdata
}

context(lua: LuaScriptEngine)
fun setupWorldTableState(world: World, table: LuaUserdata) {
    table.getmetatable().get("parameters").set("state", with(world) { world.state.coerceToLua() })
}

context(lua: LuaScriptEngine)
fun World.luaWorld(): LuaValue {
    return lua.worldsList[id.value.luaStr()]
}

fun LuaValue.asEngineWorld() = this.checkuserdata() as World

// Entity

data class LuaEntity(
    val world: LuaValue,
    val id: LuaValue
) {
    override fun toString(): String {
        return id.toint().toString()
    }
}

data class LuaEntityComponent(val entity: LuaEntity, val coercedTable: LuaValue) : Component {
    companion object {
        val TYPE by lazy { componentTypeOf(LuaEntityComponent::class) }
    }
}

fun EntityMetaTable() = luaUserdataTable<LuaEntity> {
    functionSelf("exists") { entity ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        with(world) {
            entityId.exists().luaBool()
        }
    }
    functionSelf2("get_component") { entity, component ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        with(world) {
            entityId.getLuaScriptComponent(component.componentType) ?: NIL
        }
    }
    functionSelf2("has_component") { entity, component ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        with(world) {
            entityId.hasLuaScriptComponent(component.componentType).luaBool()
        }
    }
    functionSelf2("set_component") { entity, component ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.componentType
        with(world) {
            entityId.setLuaScriptComponent(component, type)
        }
        debugScript("entity", "($entity) ${type.id} added")
        NIL
    }
    functionSelf2("remove_component") { entity, component ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.componentType
        debugScript("entity", "($entity) ${type.id} removed")
        with(world) {
            entityId.removeLuaScriptComponent(type) ?: NIL
        }
    }
    functionSelf2("mark_dirty") { entity, component ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.componentType
        debugScript("entity", "($entity) ${type.id} marked for sync")
        world.markDirty(entityId, type)
        NIL
    }
    functionSelf("get_all_components") { entity ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        world.getComponents(entityId)
            .filterIsInstance<ScriptComponent>()
            .filter { it.value is LuaTable }
            .toLuaList { it.castLua().luaValue }
    }
    functionSelf("destroy") { entity ->
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        debugScript("entity", "($entity) destroyed")
        world.destroy(entityId)
        NIL
    }
}

context(lua: LuaScriptEngine, world: World)
fun EntityId.coerceToLua(): LuaValue {
    val idL = luaNum()
    val worldL = world.luaWorld()
    return getComponent<LuaEntityComponent>()?.coercedTable ?: run {
        val metatable = LuaTable()
        metatable.set("__index", lua.entityMetaTable)
        val entity = LuaEntity(worldL, idL)
        val entityTable = CoerceJavaToLua.coerce(entity)
        entityTable.setmetatable(metatable)
        setComponent(LuaEntityComponent(entity, entityTable))
        entityTable
    }
}

fun LuaValue.asEngineEntity() = this.checkuserdata() as LuaEntity