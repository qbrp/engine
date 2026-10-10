package org.lain.engine.script.lua

import org.lain.cyberia.ecs.*
import org.lain.engine.script.*
import org.lain.engine.script.dev.ScriptInspectionValue
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
    override val inspectionValueNode: ScriptInspectionValue
        get() = with(lua) { resolveLuaInspectionValue(luaValue) }

    override fun toString(): String {
        return "${type.id}($value)"
    }
}

context(lua: LuaScriptEngine)
private fun resolveLuaInspectionValue(luaValue: LuaValue): ScriptInspectionValue {
    return when (val scriptValue = luaValue.toScriptValue()) {
        is STable -> LuaInspectionTable(luaValue.checktable(), scriptValue, lua)
        is SList -> LuaInspectionCollection(luaValue.checktable(), scriptValue, lua)
        is ScriptValuePrimitive  -> LuaInspectionPrimitive(luaValue, scriptValue)
        is SJvm -> LuaInspectionJvm(scriptValue.value)
    }
}

open class LuaInspectionValue(
    open val luaValue: LuaValue,
    open val value: ScriptValue
) : ScriptInspectionValue {
    override val identity: Any
        get() = luaValue
}

class LuaInspectionTable(
    override val luaValue: LuaTable,
    override val value: STable,
    private val lua: LuaScriptEngine
) : LuaInspectionValue(luaValue, value), ScriptInspectionValue.Table {
    override fun child(key: ScriptValue): ScriptInspectionValue = with(lua) {
        luaValue.get(key.toLuaValue()).nullable()?.let {
            resolveLuaInspectionValue(it)
        } ?: error("Table $luaValue doesnt contains child value $key")
    }

    override fun set(key: ScriptValue, value: ScriptValue) = with(lua) {
        luaValue.set(key.toLuaValue(), value.toLuaValue())
    }
}

class LuaInspectionCollection(
    override val luaValue: LuaTable,
    override val value: SList,
    private val lua: LuaScriptEngine
) : LuaInspectionValue(luaValue, value), ScriptInspectionValue.List {
    override fun child(index: Int): ScriptInspectionValue = with(lua) {
        luaValue.get(index).nullable()?.let {
            resolveLuaInspectionValue(it)
        } ?: error("Collection $luaValue doesnt contains child value at index $index")
    }

    override fun set(index: Int, value: ScriptValue) = with(lua) {
        luaValue.set(index, value.toLuaValue())
    }
}

class LuaInspectionPrimitive(
    override val luaValue: LuaValue,
    override val value: ScriptValuePrimitive
) : LuaInspectionValue(luaValue, value), ScriptInspectionValue.Primitive

class LuaInspectionJvm(override val value: Any) : ScriptInspectionValue.Jvm

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
