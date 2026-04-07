package org.lain.engine.script.lua

import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.world
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.TwoArgFunction

fun LuaValue.nullable() = if (isnil()) null else this

fun LuaTable.toStringMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    for (key in keys()) {
        map[key.tojstring()] = get(key).tojstring()
    }
    return map
}

fun <V> LuaTable.toMap(valueTransform: (LuaValue) -> V): Map<String, V> {
    val map = mutableMapOf<String, V>()
    for (key in keys()) {
        map[key.tojstring()] = valueTransform(get(key))
    }
    return map
}

fun luaTableOf(vararg values: LuaValue): LuaTable {
    return LuaTable.tableOf(values)
}

fun luaValue(string: String) = LuaValue.valueOf(string)
fun luaValueNullable(string: String?) = LuaValue.valueOf(string) ?: LuaValue.NIL

fun luaValue(int: Int) = LuaValue.valueOf(int)

fun luaValue(float: Float) = LuaValue.valueOf(float.toDouble())

fun luaValue(boolean: Boolean) = LuaValue.valueOf(boolean)

fun <V> LuaTable.toList(valueTransform: (LuaValue) -> V): List<V> {
    val list = mutableListOf<V>()
    for (i in 1..this.length()) {
        list.add(valueTransform(this.get(i)))
    }
    return list
}

fun LuaValue.toVector3f(): Vec3 {
    val elements = checktable().toList { it.tofloat() }
    require(elements.size == 3) { "Invalid vector elements count: $elements" }
    return Vec3(elements[0], elements[1], elements[2])
}

fun LuaValue.coerceToScriptComponent(): ScriptComponentType {
    return checkuserdata(ScriptComponentType::class.java) as? ScriptComponentType ?: error("Invalid component type value")
}

fun LuaValue.coerceToEnginePlayer() = this.checkuserdata() as EnginePlayer

context(context: LuaContext)
fun EnginePlayer.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> luaValue(this@coerceToLua.id.toString())
                        "world_id" -> luaValue(this@coerceToLua.world.id.toString())
                        "entity_id" -> luaValue(this@coerceToLua.entityId)
                        else -> context.playerTable.get(key)
                    }
                }
            })
        }
    }

    userdata.setmetatable(meta)
    return userdata
}