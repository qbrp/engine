package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.*
import org.lain.engine.script.toScriptComponentId
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue

fun LuaValue.asEngineScriptComponentType(): ScriptComponentType {
    return checkuserdata(ScriptComponentType::class.java) as? ScriptComponentType
        ?: error("Invalid component type value")
}

class ComponentLibrary(
    private val namespacedStorageAccess: NamespacedStorageAccess,
    private var ready: Boolean = false
) {
    private val type = LuaUserdataType<ComponentType<*>> {
        indexSelf { self, key ->
            when (key.tojstring()) {
                "id" -> self.id.luaStr()
                else -> NIL
            }
        }
    }

    val library = luaTable {
        functionV("get_type") { varargs ->
            if (!ready) {
                LuaValue.varargsOf(
                    NIL,
                    false.luaBool()
                )
            } else {
                val idRef = varargs.arg1()
                val id = idRef.resolveIdReference().toScriptComponentId()
                val foundType = CoreScriptComponents.get(id) ?: namespacedStorageAccess.components[id]
                    ?: error("unknown component type: $id")
                LuaValue.varargsOf(
                    coerceComponentType(foundType),
                    true.luaBool()
                )
            }
        }
    }

    fun coerceComponentType(type: ComponentType<*>): LuaUserdata {
        return this.type.newInstance(type)
    }

    fun setupSimulation() {
        ready = true
    }
}