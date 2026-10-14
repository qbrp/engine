package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.exists
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.asUserdataOrThrow
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.luaUserdataTable
import org.lain.engine.script.lua.toLuaTable
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World
import org.luaj.vm2.LuaInteger
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

data class LuaEntity(
    val writeAccess: WriteComponentAccess,
    val readAccess: ReadComponentAccess?,
    val world: World? = null,
    val id: Int,
    val worldL: LuaValue,
    val idL: LuaInteger
) {
    override fun toString(): String {
        return id.toString()
    }
}

data class LuaEntityComponent(val entity: LuaEntity, val coercedTable: LuaValue) : Component {
    companion object {
        val TYPE by lazy { componentTypeOf(LuaEntityComponent::class) }
    }
}

context(lua: LuaScriptEngine)
fun EntityMetaTable() = luaUserdataTable<LuaEntity> {
    functionSelf("exists") { entity ->
        val entityId = entity.id
        val world = entity.readAccess ?: error("entity hasn't read component access")
        with(world) {
            entityId.exists().luaBool()
        }
    }
    functionSelf2("get_component") { entity, typeHolder ->
        val entityId = entity.id
        val world = entity.readAccess ?: error("entity hasn't read component access")
        with(world) {
            entityId.getComponentLua(entity.world, typeHolder.fetchHolderLuaComponentAccess()) ?: NIL
        }
    }
    functionSelf2("has_component") { entity, type ->
        val entityId = entity.id
        val world = entity.readAccess ?: error("entity hasn't read component access")
        with(world) {
            entityId.hasComponentLua(type.fetchHolderLuaComponentAccess()).luaBool()
        }
    }
    functionSelf3("set_component") { entity, accessL, component ->
        val entityId = entity.id
        val world = entity.writeAccess
        val access = accessL.fetchHolderLuaComponentAccess()
        with(world) {
            entityId.setComponentLua(entity.world, component, access)
        }
        debugScript("entity", "($entity) ${access.id} added")
        component
    }
    functionSelf2("remove_component") { entity, typeL ->
        val entityId = entity.id
        val world = entity.writeAccess
        val type = typeL.fetchHolderLuaComponentAccess()
        debugScript("entity", "($entity) ${type.id} removed")
        with(world) {
            entityId.removeComponentLua(entity.world, type) ?: NIL
        }
    }
    functionSelf2("mark_updated") { entity, typeL ->
        val entityId = entity.id
        val world = entity.world ?: error("entity hasn't write component access")
        val type = typeL.fetchHolderLuaComponentAccess()
        debugScript("entity", "($entity) ${type.id} marked for sync")
        world.componentManager.markDirty(
            entityId,
            when(val access = typeL.fetchHolderLuaComponentAccess()) {
                is LuaComponentAccess.Native -> access.binding.componentType
                is LuaComponentAccess.Script -> access.type
            }
        )
        NIL
    }
    functionSelf("list_components") { entity ->
        val entityId = entity.id
        val world = entity.world ?: error("entity hasn't read component access")
        world.getComponents(entityId)
            .filterIsInstance<LuaScriptComponent>()
            .associate { it.type to it.luaValue }
            .toLuaTable(
                { lua.componentLibrary.scriptComponentAccess(it) },
                { it }
            )
    }
    functionSelf("destroy") { entity ->
        val entityId = entity.id
        val world = entity.writeAccess
        debugScript("entity", "($entity) destroyed")
        world.destroy(entityId)
        NIL
    }
    indexSelf { self, key ->
        when (key.tojstring()) {
            "world" -> self.worldL
            "id" -> self.idL
            "readable" -> (self.readAccess != null).luaBool()
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine, world: World)
fun EntityId.luaEntity(): LuaValue {
    return getComponent<LuaEntityComponent>()?.coercedTable ?: run {
        val idL = luaNum()
        val entity = LuaEntity(world, world, world, this, world.luaWorld(), idL)
        val entityTable = LuaUserdata(entity)
        entityTable.setmetatable(lua.entityMetaTable)
        setComponent(LuaEntityComponent(entity, entityTable))
        entityTable
    }
}

fun LuaValue.checkLuaEntity(): Int {
    return asUserdataOrThrow<LuaEntity>().id
}

context(lua: LuaScriptEngine, access: WriteComponentAccess)
fun EntityId.luaWritableEntity(): LuaUserdata {
    val entityId = this@luaWritableEntity
    val entity = LuaEntity(
        access, null, null, entityId, NIL, entityId.luaNum()
    )
    val table = LuaUserdata(entity)
    table.setmetatable(lua.entityMetaTable)
    return table
}
