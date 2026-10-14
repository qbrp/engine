package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.*
import org.lain.engine.script.toScriptComponentId
import org.lain.engine.world.World
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue

class ComponentLibrary(
    private val namespacedStorageAccess: NamespacedStorageAccess,
    private var ready: Boolean = false,
    private val lua: LuaScriptEngine
) {
    private val componentTypeHolder = with(lua) {
        LuaUserdataType<LuaComponentAccess> {
            indexSelf { self, key ->
                when (key.tojstring()) {
                    "id" -> self.id.coerceToLua()
                    else -> NIL
                }
            }
        }
    }

    val library = luaTable {
        functionV("get_type") { varargs ->
            if (!ready) {
                LuaValue.varargsOf(NIL, false.luaBool())
            } else {
                val idRef = varargs.arg1()
                val id = idRef.resolveIdReference().toScriptComponentId()
                LuaValue.varargsOf(
                    scriptComponentAccess(resolveComponentType(id)),
                    true.luaBool()
                )
            }
        }

        functionV("get_binding") { varargs ->
            if (!ready) {
                LuaValue.varargsOf(NIL, false.luaBool())
            } else {
                val idRef = varargs.arg1()
                val id = LuaComponentBindingId(idRef.resolveIdReference())
                LuaValue.varargsOf(
                    bindingComponentAccess(LuaComponentBindingRegistry.get(id)),
                    true.luaBool()
                )
            }
        }
    }

    fun scriptComponentAccess(type: ScriptComponentType): LuaUserdata {
        return this.componentTypeHolder.newInstance(
            LuaComponentAccess.Script(type)
        )
    }

    fun bindingComponentAccess(binding: LuaComponentBinding<*>): LuaUserdata {
        return this.componentTypeHolder.newInstance(
            LuaComponentAccess.Native(binding)
        )
    }

    fun setupSimulation() {
        ready = true
    }

    private fun resolveComponentType(id: ScriptComponentId): ScriptComponentType {
        return CoreScriptComponents.get(id) ?: namespacedStorageAccess.components[id] ?: error("unknown component type: $id")
    }
}