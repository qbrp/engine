package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaUserdataTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toVoxelPos
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
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

    functionSelf4("emit") { world, accessL, event, networkedL ->
        val access = accessL.fetchComponentAccess()
        val component = when(access) {
            is LuaComponentAccess.Native -> access.asGeneric().fromLua(world.state, event)
                ?: error("could not create component ${access.componentType} for event as binding")
            is LuaComponentAccess.Script -> LuaScriptComponent(event, access.type, lua)
        }
        val networked = networkedL.toboolean()
        with(world) {
            world.emitEvent(
                component,
                access.componentType,
                networked
            ).luaEntity()
        }
    }

    function3("iterate") { self, types, func ->
        val world = self.asEngineWorld()
        val tick = world.simulation.ticks
        val accesses = types.checktable().toList { it.fetchComponentAccess() }
        val entityArray = world.componentManager.getComponentArray(LuaEntityComponent.TYPE)

        fun getOrCreateEntityComponent(entityId: EntityId): LuaEntityComponent {
            return entityArray.componentOf(entityId) ?: with(world) { entityId.cacheLuaEntity() }
        }

        fun Component.luaPresentation(entity: EntityId, access: LuaComponentAccess): LuaValue = with(world) {
            return when (access) {
                is LuaComponentAccess.Native -> {
                    val cachedType = access.cachedComponentType
                    cachedType
                        ?.let { entity.getComponent(it) }
                        ?.takeIf { it.tick == tick }
                        ?.component
                        ?: access.asGeneric().toLua(world, entity, this@luaPresentation)
                            .also { value ->
                                cachedType?.let { type ->
                                    entity.setComponent(LuaCachedComponentBinding(tick, value), type)
                                }
                            }
                }
                is LuaComponentAccess.Script -> (this@luaPresentation as ScriptComponent).castedLuaValue
            }
        }

        when (accesses.size) {
            1 -> {
                val access1 = accesses[0]
                world.iterate1(access1.componentType) { entity, component ->
                    val luaEntity = getOrCreateEntityComponent(entity)
                    func.invoke(
                        self,
                        luaEntity.coercedTable,
                        component.luaPresentation(entity, access1)
                    )
                }
            }

            2 -> {
                val access1 = accesses[0]
                val access2 = accesses[1]
                world.iterate2(
                    access1.componentType,
                    access2.componentType
                ) { entity, component1, component2 ->
                    val luaEntity = getOrCreateEntityComponent(entity)
                    func.invoke(
                        arrayOf(
                            self,
                            luaEntity.coercedTable,
                            component1.luaPresentation(entity, access1),
                            component2.luaPresentation(entity, access2)
                        )
                    )
                }
            }

            3 -> {
                val access1 = accesses[0]
                val access2 = accesses[1]
                val access3 = accesses[2]
                world.iterate3(
                    access1.componentType,
                    access2.componentType,
                    access3.componentType
                ) { entity, component1, component2, component3 ->
                    val luaEntity = getOrCreateEntityComponent(entity)
                    func.invoke(
                        arrayOf(
                            self,
                            luaEntity.coercedTable,
                            component1.luaPresentation(entity, access1),
                            component2.luaPresentation(entity, access2),
                            component3.luaPresentation(entity, access3)
                        )
                    )
                }
            }

            4 -> {
                val access1 = accesses[0]
                val access2 = accesses[1]
                val access3 = accesses[2]
                val access4 = accesses[3]
                world.iterate4(
                    access1.componentType,
                    access2.componentType,
                    access3.componentType,
                    access4.componentType
                ) { entity, component1, component2, component3, component4 ->
                    val luaEntity = getOrCreateEntityComponent(entity)
                    func.invoke(
                        arrayOf(
                            self,
                            luaEntity.coercedTable,
                            component1.luaPresentation(entity, access1),
                            component2.luaPresentation(entity, access2),
                            component3.luaPresentation(entity, access3),
                            component4.luaPresentation(entity, access4)
                        )
                    )
                }
            }

            5 -> {
                val access1 = accesses[0]
                val access2 = accesses[1]
                val access3 = accesses[2]
                val access4 = accesses[3]
                val access5 = accesses[4]
                world.iterate5(
                    access1.componentType,
                    access2.componentType,
                    access3.componentType,
                    access4.componentType,
                    access5.componentType
                ) { entity, component1, component2, component3, component4, component5 ->
                    val luaEntity = getOrCreateEntityComponent(entity)
                    func.invoke(
                        arrayOf(
                            self,
                            luaEntity.coercedTable,
                            component1.luaPresentation(entity, access1),
                            component2.luaPresentation(entity, access2),
                            component3.luaPresentation(entity, access3),
                            component4.luaPresentation(entity, access4),
                            component5.luaPresentation(entity, access5)
                        )
                    )
                }
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