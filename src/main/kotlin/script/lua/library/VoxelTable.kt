package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.castLua
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.world.VoxelDoor
import org.lain.engine.world.VoxelMeta
import org.lain.engine.world.VoxelTag
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction

fun VoxelMetaUserdataType() = LuaUserdataType<VoxelMeta> {
    functionSelf2("has_tag") { self, tag ->
        self.hasTag(VoxelTag(tag.tojstring())).luaBool()
    }

    indexSelf { self, key ->
        when(key.tojstring()) {
            "id" -> self.id.luaStr()
            "tags" -> self.tags.toList().toLuaList { it.value.luaStr() }
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun VoxelMeta.coerceToLua(): LuaUserdata = lua.voxelMetaUserdataType.newInstance(this)

fun World.applyLuaVoxelDoorComponents() {
    iterate(CoreScriptComponents.VOXEL_DOOR) { entity, door ->
        val lOpen = door.castLua().luaValue["open"].toboolean()
        val kDoor = entity.getComponent<VoxelDoor>() ?: run {
            val component = VoxelDoor(lOpen)
            entity.setComponent(component)
            component
        }
        kDoor.open = lOpen
    }
}