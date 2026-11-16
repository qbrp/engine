package org.lain.engine.script.lua.library

import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.script.lua.luaBool
import org.lain.engine.util.ecs.ComponentState
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

private fun ComponentState.getLuaComponent(typeHolder: LuaValue): LuaValue? {
    return getComponent(typeHolder.fetchComponentAccess().asScript())?.castedLuaValue
}

context(lua: LuaScriptEngine)
fun ComponentStateUserdataType() = LuaUserdataType<ComponentState> {
    functionSelf2("get_component") { state, typeHolder ->
         state.getLuaComponent(typeHolder) ?: NIL
    }
    functionSelf2("require_component") { state, typeHolder ->
        val componentType = typeHolder.fetchComponentAccess().asScript()
        state.getComponent(componentType)?.castedLuaValue
            ?: error("State does not contains component ${componentType.id}")
    }
    functionSelf2("has_component") { state, type ->
        val result = state.getLuaComponent(type) != null
        result.luaBool()
    }
    functionSelf3("set_component") { state, typeHolder, component ->
        val componentType = typeHolder.fetchComponentAccess().asScript()
        state.setComponent(componentType, LuaScriptComponent(component, componentType, lua))
        component
    }
    functionSelf2("remove_component") { state, typeHolder ->
        val componentType = typeHolder.fetchComponentAccess().asScript()
        state.removeComponent(componentType)?.castedLuaValue ?: NIL
    }
    indexSelf { _, _ -> NIL }
}
