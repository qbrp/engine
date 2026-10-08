package org.lain.engine.script.lua.library

import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.library.ecs.projectLuaComponent
import org.lain.engine.world.VoxelDoor
import org.lain.engine.world.VoxelMeta
import org.lain.engine.world.VoxelTag
import org.lain.engine.world.World
import org.luaj.vm2.LuaUserdata

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

fun World.pullVoxelDoor() {
    projectLuaComponent<VoxelDoor>(CoreScriptComponents.VOXEL_DOOR) { value, _ ->
        VoxelDoor(value["open"].toboolean())
    }
}
