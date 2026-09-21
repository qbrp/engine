package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.*
import org.lain.engine.script.toScriptComponentId
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue

fun LuaValue.asEngineScriptComponentType(): ScriptComponentType {
    return checkuserdata(ScriptComponentType::class.java) as? ScriptComponentType
        ?: error("Invalid component type value")
}

fun LuaValue.fetchComponentTypeFromHolder(): ScriptComponentType {
    return get("type").asEngineScriptComponentType()
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
                LuaValue.varargsOf(NIL, false.luaBool())
            } else {
                val idRef = varargs.arg1()
                val id = idRef.resolveIdReference().toScriptComponentId()
                LuaValue.varargsOf(
                    coerceComponentType(resolveComponentType(id)),
                    true.luaBool()
                )
            }
        }

        functionV("get_holder") { varargs ->
            if (!ready) {
                LuaValue.varargsOf(NIL, false.luaBool())
            } else {
                val componentTypeReference = varargs.arg1()
                LuaValue.varargsOf(
                    luaTable {
                        "type"(
                            when (componentTypeReference.type()) {
                                LuaValue.TUSERDATA -> if (componentTypeReference.isuserdata(ScriptComponentType::class.java)) {
                                    componentTypeReference
                                } else {
                                    coerceComponentType(
                                        resolveComponentType(
                                            componentTypeReference.asEngineId().toScriptComponentId()
                                        )
                                    )
                                }

                                else -> error("Cannot resolve component type reference: $componentTypeReference")
                            }
                        )
                    },
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

    private fun resolveComponentType(id: ScriptComponentId): ScriptComponentType {
        return CoreScriptComponents.get(id) ?: namespacedStorageAccess.components[id]
        ?: error("unknown component type: $id")
    }
}