package org.lain.engine.script.lua

import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptComponentType
import org.luaj.vm2.Globals

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

context(ctx: LuaContext)
fun ComponentTable() = luaTable {
    function1("type_of") { idL ->
        val id = idL.tojstring()
        val type = LazyScriptComponentType(ctx.dependencies.namespacesStorage, ScriptComponentId(id))
        type.toLuaValue()
    }
}