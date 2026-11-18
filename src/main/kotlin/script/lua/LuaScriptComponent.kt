package org.lain.engine.script.lua

import org.lain.engine.script.*
import org.lain.engine.script.lua.library.LuaEntity
import org.lain.engine.util.property.ScriptValueBinding
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaScriptComponent(
    val luaValue: LuaValue,
    override val type: ScriptComponentType,
    private val lua: LuaScriptEngine
) : ScriptComponent {
    override val value: ScriptValue
        get() = with(lua) { luaValue.toScriptValue() }
    override val inspectionValueNode: ScriptValueBinding
        get() = with(lua) { resolveLuaInspectionValue(luaValue) }

    override fun toString(): String {
        return "${type.id}($value)"
    }
}

context(lua: LuaScriptEngine)
private fun resolveLuaInspectionValue(luaValue: LuaValue): ScriptValueBinding {
    return when (val scriptValue = luaValue.toScriptValue()) {
        is STable -> LuaTableBinding(luaValue.checktable(), scriptValue, lua)
        is SList -> LuaCollectionBinding(luaValue.checktable(), scriptValue, lua)
        is ScriptValuePrimitive  -> LuaPrimitiveBinding(luaValue, scriptValue)
        is SJvm -> when (val value = scriptValue.value) {
            is LuaEntity -> LuaPrimitiveBinding(luaValue, SEntityRef(value.id))
            else -> LuaJvmBinding(value)
        }
        SNil -> error("Nil does not have an inspectable value")
    }
}

open class LuaValueBinding(
    open val luaValue: LuaValue,
    open val value: ScriptValue
) : ScriptValueBinding {
    override val identity: Any
        get() = luaValue
}

class LuaTableBinding(
    override val luaValue: LuaTable,
    override val value: STable,
    private val lua: LuaScriptEngine
) : LuaValueBinding(luaValue, value), ScriptValueBinding.Table {
    override val entries: List<ScriptValueBinding.Table.Entry>
        get() = with(lua) {
            luaValue.keys().map { keyL ->
                val valueL = luaValue[keyL]
                val value = resolveLuaInspectionValue(valueL)
                val key = resolveLuaInspectionValue(keyL)
                ScriptValueBinding.Table.Entry(key, value)
            }
        }

    override fun set(key: ScriptValue, value: ScriptValue) = with(lua) {
        val luaKey = key.toLuaValue()
        luaValue.set(luaKey, value.toLuaValue())
    }

    override fun replace(entry: ScriptValueBinding.Table.Entry, value: ScriptValue) = with(lua) {
        val liveKey = (entry.key as? LuaValueBinding)?.luaValue
            ?: error("Entry does not contain a Lua key")
        luaValue.set(liveKey, value.toLuaValue())
    }
}

class LuaCollectionBinding(
    override val luaValue: LuaTable,
    override val value: SList,
    private val lua: LuaScriptEngine
) : LuaValueBinding(luaValue, value), ScriptValueBinding.List {
    override val values: List<ScriptValueBinding>
        get() = with(lua) {
            List(value.values.size) { index ->
                resolveLuaInspectionValue(luaValue[index + 1])
            }
        }

    override fun set(index: Int, value: ScriptValue) = with(lua) {
        luaValue.set(index + 1, value.toLuaValue())
    }

    override fun add(value: ScriptValue): Unit = with(lua) {
        luaValue.set(luaValue.keyCount() + 1, value.toLuaValue())
    }
}

class LuaPrimitiveBinding(
    override val luaValue: LuaValue,
    override val value: ScriptValuePrimitive
) : LuaValueBinding(luaValue, value), ScriptValueBinding.Primitive

class LuaJvmBinding(override val value: Any) : ScriptValueBinding.Jvm

fun ScriptComponent.castLua() = this as LuaScriptComponent

val ScriptComponent.castedLuaValue
    get() = castLua().luaValue
