package org.lain.engine.script.lua

import org.lain.cyberia.ecs.*
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
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

context(lua: LuaContext)
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
                e.setScriptComponent(component, type.requireType())
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
            debugScript("voxel", "($entity) created dynamic voxel, networked = $networked")
            entity.coerceToLua()
        }
    }

    function2("get_dynamic_voxel") { self, pos ->
        val world = self.asEngineWorld()
        val voxelPos = pos.toVoxelPos()
        with(world) { chunkStorage.getDynamicVoxel(voxelPos)?.coerceToLua() ?: LuaValue.NIL }
    }

    function3("emit") { self, event, networkedL ->
        val world = self.asEngineWorld()
        val eventType = event.get("type").asEngineScriptComponentType().requireType()
        val networked = networkedL.toboolean()
        with(world) {
            world.emitEvent(
                ScriptComponent(event, eventType),
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
            1 -> world.iterate1(typesL[0].ecsType) { entity, component ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    self,
                    luaEntity.coercedTable,
                    component.luaValue
                )
            }

            2 -> world.iterate2(
                typesL[0].ecsType,
                typesL[1].ecsType
            ) { entity, component1, component2 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaValue,
                        component2.luaValue
                    )
                )
            }

            3 -> world.iterate3(
                typesL[0].ecsType,
                typesL[1].ecsType,
                typesL[2].ecsType
            ) { entity, component1, component2, component3 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaValue,
                        component2.luaValue,
                        component3.luaValue
                    )
                )
            }

            4 -> world.iterate4(
                typesL[0].ecsType,
                typesL[1].ecsType,
                typesL[2].ecsType,
                typesL[3].ecsType
            ) { entity, component1, component2, component3, component4 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaValue,
                        component2.luaValue,
                        component3.luaValue,
                        component4.luaValue
                    )
                )
            }

            5 -> world.iterate5(
                typesL[0].ecsType,
                typesL[1].ecsType,
                typesL[2].ecsType,
                typesL[3].ecsType,
                typesL[4].ecsType
            ) { entity, component1, component2, component3, component4, component5 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaValue,
                        component2.luaValue,
                        component3.luaValue,
                        component4.luaValue,
                        component5.luaValue
                    )
                )
            }
        }
        NIL
    }
}

context(lua: LuaContext)
fun World.coerceToLua(): LuaUserdata {
    val world = this

    val parameters = luaTable {
        "id"(id.value.toLuaValue())
        "is_client"(isClient.toLuaValue())
    }

    val userdata = LuaUserdata(world)
    userdata.setmetatable(
        luaTable {
            "parameters"(parameters)
            index { self, key ->
                if (key.tojstring() == "players") {
                    players.toLuaArray { it.coerceToLua() }
                } else {
                    parameters.get(key)?.nullable() ?: lua.worldMetaTable.get(key)
                }
            }
        }
    )

    return userdata
}

context(lua: LuaContext)
fun setupWorldTableState(world: World, table: LuaUserdata) {
    table.getmetatable().get("parameters").set("state", with(world) { world.state.coerceToLua() })
}

context(lua: LuaContext)
fun World.getLuaValue(): LuaValue {
    return lua.worldsList[id.value.toLuaValue()]
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

fun EntityMetaTable() = luaTable {
    function1("exists") { self ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        with(world) { entityId.exists() }.toLuaValue()
    }
    function2("get_component") { self, component ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.checktable().get("type").asEngineScriptComponentType()
        world.getLuaComponent(entityId, type.requireType()) ?: NIL
    }
    function2("has_component") { self, component ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.checktable().get("type").asEngineScriptComponentType()
        luaValue(world.hasLuaComponent(entityId, type.requireType()))
    }
    function2("set_component") { self, component ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.checktable().get("type").asEngineScriptComponentType()
        with(world) { entityId.setScriptComponent(component, type.requireType()) }
        debugScript("entity", "($entity) ${type.id} added")
        NIL
    }
    function2("remove_component") { self, component ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.checktable().get("type").asEngineScriptComponentType()
        debugScript("entity", "($entity) ${type.id} removed")
        world.removeLuaComponent(entityId, type.requireType()) ?: NIL
    }
    function2("mark_dirty") { self, component ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        val type = component.checktable().get("type").asEngineScriptComponentType()
        debugScript("entity", "($entity) ${type.id} marked for sync")
        world.markDirty(entityId, type.requireType())
        LuaValue.NIL
    }
    function1("get_all_components") { self ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        world.getComponents(entityId)
            .filterIsInstance<ScriptComponent>()
            .filter { it.value is LuaTable }
            .toLuaArray { it.luaValue }
    }
    function1("destroy") { self ->
        val entity = self.asEngineEntity()
        val entityId = entity.id.toint()
        val world = entity.world.asEngineWorld()
        debugScript("entity", "($entity) destroyed")
        world.destroy(entityId)
        NIL
    }
}

context(lua: LuaContext, world: World)
fun EntityId.coerceToLua(): LuaValue {
    val idL = luaValue(this)
    val worldL = world.getLuaValue()
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

val ScriptComponent.luaValue
    get() = value as? LuaValue ?: error("Component not supports lua")

private fun World.hasLuaComponent(entityId: EntityId, componentType: ScriptComponentType): Boolean {
    return hasComponent(entityId, componentType)
}

private fun World.getLuaComponent(entityId: EntityId, componentType: ScriptComponentType): LuaValue? {
    val component = getComponent(entityId, componentType) ?: return null
    return (component.value as? LuaValue) ?: error("Component ${componentType.id} not supports lua")
}

private fun World.removeLuaComponent(entityId: EntityId, componentType: ScriptComponentType): LuaValue? {
    val component = removeComponent(entityId, componentType) ?: return null
    if (component.value !is LuaValue) error("Removed non-lua component with type ${componentType.id}")
    return component.value
}
