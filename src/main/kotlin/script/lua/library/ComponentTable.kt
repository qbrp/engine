package org.lain.engine.script.lua.library

import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaValue
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.TwoArgFunction

class LazyScriptComponentType(
    private val storage: NamespacedStorageAccess,
    val id: ScriptComponentId
) {
    private var componentType: ScriptComponentType? = null
    val ecsType get() = requireType().ecsType

    fun getType(): ScriptComponentType? {
        return storage.components[id] ?: CoreScriptComponents.get(id)
    }

    fun requireType(): ScriptComponentType {
        return componentType ?: (getType() ?: error("Component $id not registered in system"))
            .also { componentType = it }
    }
}

context(ctx: LuaScriptEngine)
fun ComponentTable() = luaTable {
    function1("type_of") { idL ->
        val id = idL.tojstring()
        val type = LazyScriptComponentType(ctx.dependencies.namespacesStorage, ScriptComponentId(id))
        type.toLuaValue()
    }
}

fun LazyScriptComponentType.toLuaValue(): LuaValue {
    val userdata = LuaUserdata(this)

    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> {
                            luaValue(requireType().id)
                        }
                        else -> NIL
                    }
                }
            })
        }
    }
    userdata.setmetatable(meta)
    return userdata
}

fun LuaValue.asEngineScriptComponentType(): LazyScriptComponentType {
    return checkuserdata(LazyScriptComponentType::class.java) as? LazyScriptComponentType
        ?: error("Invalid component type value")
}

val LuaValue.lazyComponentType: LazyScriptComponentType
    get() = checktable()
        .get("type")
        .asEngineScriptComponentType()

val LuaValue.componentType: ScriptComponentType
    get() = lazyComponentType.requireType()