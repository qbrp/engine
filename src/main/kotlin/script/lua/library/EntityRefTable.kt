package org.lain.engine.script.lua.library

import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.luaNum
import org.lain.cyberia.ecs.EntityId

fun EntityRefUserdataType() = LuaUserdataType<EntityId> {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "id" -> self.index.luaNum()
            "index" -> self.index.luaNum()
            "generation" -> self.generation.luaNum()
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun EntityId.luaEntityRef() = lua.entityRefUserdataType.newInstance(this)