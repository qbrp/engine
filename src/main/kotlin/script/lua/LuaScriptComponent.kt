package org.lain.engine.script.lua

import org.lain.cyberia.ecs.*
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptDebugTarget
import org.lain.engine.script.ScriptValue
import org.lain.engine.util.ecs.EntityId
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaScriptComponent(
    val luaValue: LuaValue,
    override val type: ScriptComponentType,
    private val lua: LuaScriptEngine
) : ScriptComponent {
    override val value: ScriptValue
        get() = with(lua) { luaValue.toScriptValue() }
    override val debugTarget: ScriptDebugTarget?
        get() = if (luaValue.istable()) LuaScriptDebugTarget(luaValue.checktable(), lua) else null

    override fun toString(): String {
        return "${type.id}($value)"
    }
}

private class LuaScriptDebugTarget(
    private val table: LuaTable,
    private val lua: LuaScriptEngine
) : ScriptDebugTarget {
    override val identity: Any
        get() = table

    override fun child(key: ScriptValue): ScriptDebugTarget? = with(lua) {
        val value = table.get(key.toLuaValue())
        return if (value.istable()) LuaScriptDebugTarget(value.checktable(), lua) else null
    }

    override fun set(property: String, value: ScriptValue) = with(lua) {
        table.set(property, value.toLuaValue())
    }
}

context(writeComponentAccess: WriteComponentAccess, lua: LuaScriptEngine)
fun EntityId.setLuaScriptComponent(value: LuaValue, type: ScriptComponentType): LuaScriptComponent {
    val component = LuaScriptComponent(value, type, lua)
    setComponent(component, type)
    return component
}

context(read: ReadComponentAccess)
fun EntityId.hasLuaScriptComponent(componentType: ScriptComponentType): Boolean {
    return hasComponent(componentType)
}

context(read: ReadComponentAccess)
fun EntityId.getLuaScriptComponent(componentType: ScriptComponentType): LuaValue? {
    val component = getComponent(componentType) ?: return null
    component as? LuaScriptComponent ?: error("Компонент $component не принадлежит Lua")
    return component.luaValue
}

context(world: WriteComponentAccess)
fun EntityId.removeLuaScriptComponent(componentType: ScriptComponentType): LuaValue? {
    val component = removeComponent(componentType) ?: return null
    component as? LuaScriptComponent ?: error("Компонент $component не принадлежит Lua")
    return component.luaValue
}

fun ScriptComponent.castLua() = this as LuaScriptComponent

val ScriptComponent.castedLuaValue
    get() = castLua().luaValue
