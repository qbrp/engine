package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.castLua
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaUserdataTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toVoxelPos
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World
import org.lain.engine.world.invokeCommand
import org.lain.engine.world.setDynamicVoxel
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

// World

context(lua: LuaScriptEngine)
fun WorldMetaTable() = luaUserdataTable<World> {
    functionSelf2("invoke_command") { world, command ->
        val commandL = command.tojstring()
        world.invokeCommand(commandL)
        NIL
    }

    functionSelf("add_entity") { world ->
        val entity = with(world) {
            world.addEntity().luaEntity()
        }
        debugScript("entity", "($entity) added")
        entity
    }

    functionSelfV("add_dynamic_voxel") { world, varargs ->
        val voxelPos = varargs.arg1().toVoxelPos()
        val networked = varargs.arg(2).nullable()?.toboolean() ?: false
        with(world) {
            val chunk = world.chunkStorage.getChunk(voxelPos)
            val existingVoxel = world.chunkStorage.getDynamicVoxel(voxelPos)
            if (chunk == null) {
                LuaValue.varargsOf(
                    NIL,
                    luaValue("chunk_not_loaded")
                )
            } else if (existingVoxel != null) {
                LuaValue.varargsOf(
                    NIL,
                    luaValue("position_occupied")
                )
            } else {
                val entity = setDynamicVoxel(voxelPos, networked)
                debugScript(
                    "voxel",
                    "($entity) created dynamic voxel, networked = $networked"
                )
                LuaValue.varargsOf(
                    entity.luaEntity(),
                    NIL
                )
            }
        }
    }

    functionSelf2("get_dynamic_voxel") { world, pos ->
        val voxelPos = pos.toVoxelPos()
        with(world) {
            chunkStorage.getDynamicVoxel(voxelPos)?.luaEntity() ?: NIL
        }
    }

    functionSelf4("emit") { world, typeL, event, networkedL ->
        val type = typeL.fetchHolderLuaComponentAccess().asScript()
        val networked = networkedL.toboolean()
        with(world) {
            world.emitEvent(
                LuaScriptComponent(event, type, lua),
                type,
                networked
            ).luaEntity()
        }
    }

    function3("iterate") { self, types, func ->
        val world = self.asEngineWorld()
        val accesses = types.checktable().toList { it.fetchHolderLuaComponentAccess() }
        val types = accesses.map { it.componentType }
        val entityArray = world.componentManager.getComponentArray(LuaEntityComponent.TYPE)
        fun getOrCreateEntityComponent(entityId: EntityId): LuaEntityComponent {
            return entityArray.componentOf(entityId) ?: run {
                with(world) { entityId.luaEntity() }
                entityArray.componentOf(entityId)!!
            }
        }

        fun Component.luaPresentation(entity: EntityId, idx: Int): LuaValue {
            return when (val access = accesses[idx]) {
                is LuaComponentAccess.Native -> access.asGeneric().toLua(world, entity, this)
                is LuaComponentAccess.Script -> (this as ScriptComponent).castedLuaValue
            }
        }

        when (accesses.size) {
            1 -> world.iterate1(types[0]) { entity, component ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    self,
                    luaEntity.coercedTable,
                    component.luaPresentation(entity, 0)
                )
            }

            2 -> world.iterate2(
                types[0],
                types[1]
            ) { entity, component1, component2 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaPresentation(entity, 0),
                        component2.luaPresentation(entity, 1)
                    )
                )
            }

            3 -> world.iterate3(
                types[0],
                types[1],
                types[2]
            ) { entity, component1, component2, component3 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaPresentation(entity, 0),
                        component2.luaPresentation(entity, 1),
                        component3.luaPresentation(entity, 2)
                    )
                )
            }

            4 -> world.iterate4(
                types[0],
                types[1],
                types[2],
                types[3]
            ) { entity, component1, component2, component3, component4 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaPresentation(entity, 0),
                        component2.luaPresentation(entity, 1),
                        component3.luaPresentation(entity, 2),
                        component4.luaPresentation(entity, 3)
                    )
                )
            }

            5 -> world.iterate5(
                types[0],
                types[1],
                types[2],
                types[3],
                types[4]
            ) { entity, component1, component2, component3, component4, component5 ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    arrayOf(
                        self,
                        luaEntity.coercedTable,
                        component1.luaPresentation(entity, 0),
                        component2.luaPresentation(entity, 1),
                        component3.luaPresentation(entity, 2),
                        component4.luaPresentation(entity, 3),
                        component5.luaPresentation(entity, 4)
                    )
                )
            }
        }
        NIL
    }

    indexSelf { self, key ->
        when(key.tojstring()) {
            "id" -> self.id.value.luaStr()
            "is_client" -> self.isClient.luaBool()
            "players" -> self.players.toLuaList { it.coerceToLua() }
            "entity" -> with(self) { self.state.luaEntity() }
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun World.luaWorld(): LuaValue {
    return lua.worldsList[id.value.luaStr()]
}

fun LuaValue.asEngineWorld() = this.checkuserdata() as World