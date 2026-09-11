package org.lain.engine.script.lua.library

import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue

fun LuaValue.asEngineIdOrNull() = when(type()) {
    LuaValue.TUSERDATA -> checkuserdata(EngineId::class.java) as? EngineId
    else -> null
}

fun LuaValue.asEngineId() = checkuserdata(EngineId::class.java) as? EngineId ?: error("Invalid id reference type")

fun LuaValue.resolveIdReference(): EngineId {
    return when(type()) {
        LuaValue.TSTRING -> EngineId.parse(tojstring())
        LuaValue.TUSERDATA -> asEngineId()
        else -> error("Invalid id reference type")
    }
}

class IdLibrary {
    private val type = LuaUserdataType<EngineId> {
        indexSelf { self, key ->
            when(key.tojstring()) {
                "namespace" -> self.namespace.luaStr()
                "loc", "local" -> self.local.luaStr()
                "full" -> self.full.luaStr()
                else -> NIL
            }
        }
    }
    val library = luaTable {
        function2("new") { namespaceL, localL ->
            newInstance(
                EngineId.parse(
                    "${namespaceL.tojstring()}/${localL.tojstring()}"
                )
            )
        }
        functionV("parse") { args ->
            val result = runCatching {
                EngineId.parse(args.arg1().tojstring())
            }
            val id = result.getOrNull()
                ?.let { newInstance(it) }
                ?: NIL
            val error = result.exceptionOrNull()?.message?.luaStr()
                ?: NIL
            LuaValue.varargsOf(id, error)
        }
        function1("fetch_local") { idL ->
            idL.asEngineIdOrNull()?.local?.luaStr() ?: EngineId.fetchLocal(idL.tojstring()).luaStr()
        }
        function1("validate_local") { localL ->
            EngineId.isValidLocal(localL.tojstring()).luaBool()
        }
    }

    fun newInstance(id: EngineId): LuaUserdata {
        return type.newInstance(id)
    }
}
