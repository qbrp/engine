package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.*
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.castLua
import org.lain.engine.script.lua.getLuaScriptComponent
import org.lain.engine.script.lua.hasLuaScriptComponent
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaUserdataTable
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.removeLuaScriptComponent
import org.lain.engine.script.lua.setLuaScriptComponent
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toVoxelPos
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World
import org.lain.engine.world.invokeCommand
import org.lain.engine.world.setDynamicVoxel
import org.luaj.vm2.LuaInteger
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
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
        val type = typeL.asEngineScriptComponentType()
        val networked = networkedL.toboolean()
        with(world) {
            world.emitEvent(
                LuaScriptComponent(event, type),
                type,
                networked
            ).luaEntity()
        }
    }

    function3("iterate") { self, types, func ->
        val world = self.asEngineWorld()
        val typesL = types.checktable().toList { it.asEngineScriptComponentType() }
        val entityArray = world.componentManager.getComponentArray(LuaEntityComponent.TYPE)
        fun getOrCreateEntityComponent(entityId: EntityId): LuaEntityComponent {
            return entityArray.componentOf(entityId) ?: run {
                with(world) { entityId.luaEntity() }
                entityArray.componentOf(entityId)!!
            }
        }

        when (typesL.size) {
            1 -> world.iterate1(typesL[0]) { entity, component ->
                val luaEntity = getOrCreateEntityComponent(entity)
                func.invoke(
                    self,
                    luaEntity.coercedTable,
                    component.castLua().luaValue
                )
            }

            2 -> world.iterate2(
                typesL[0],
                typesL[1]
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
                typesL[0],
                typesL[1],
                typesL[2]
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
                typesL[0],
                typesL[1],
                typesL[2],
                typesL[3]
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
                typesL[0],
                typesL[1],
                typesL[2],
                typesL[3],
                typesL[4]
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