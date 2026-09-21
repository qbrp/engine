package org.lain.engine.script.lua.library

import org.lain.engine.script.ScriptValue
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.luaNum
import org.lain.engine.util.ecs.EntityId
import org.luaj.vm2.LuaValue

data class LuaEntityRef(val id: LuaValue)

fun EntityRefUserdataType() = LuaUserdataType<LuaEntityRef> {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "id" -> self.id
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun EntityId.toEntityRef() = lua.entityRefUserdataType.newInstance(LuaEntityRef(luaNum()))