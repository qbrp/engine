package org.lain.engine.script.lua

import org.luaj.vm2.Lua
import org.luaj.vm2.LuaInteger
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

// Типы данных

fun luaValue(string: String) = LuaValue.valueOf(string)

fun luaValue(int: Int) = LuaValue.valueOf(int)

fun luaValue(boolean: Boolean) = LuaValue.valueOf(boolean)

fun luaValue(double: Double) = LuaValue.valueOf(double)

fun String.luaStr(): LuaValue = luaValue(this)

fun Boolean.luaBool(): LuaValue = luaValue(this)

fun Double.luaNum(): LuaValue = luaValue(this)

fun Int.luaNum(): LuaInteger = luaValue(this)

//// Коллекции

// Таблицы

fun <K, V> LuaTable.toMap(
    keyTransform: (LuaValue) -> K,
    valueTransform: (LuaValue) -> V
): Map<K, V> {
    val map = mutableMapOf<K, V>()
    for (key in keys()) {
        map[keyTransform(key)] = valueTransform(get(key))
    }
    return map
}

fun <V> LuaTable.toMap(valueTransform: (LuaValue) -> V): Map<String, V> {
    return toMap(
        keyTransform = { it.tojstring() },
        valueTransform = valueTransform
    )
}

fun <K, V> Map<K, V>.toLuaTable(
    keyTransform: (K) -> LuaValue,
    valueTransform: (V) -> LuaValue
): LuaTable {
    val table = LuaTable()
    entries.forEach { (key, value) ->
        table[keyTransform(key)] = valueTransform(value)
    }
    return table
}
fun LuaTable.toStringMap(): Map<String, String> {
    return toMap { it.tojstring() }
}

// Списки

fun <T> Collection<T>.toLuaList(transform: (T) -> LuaValue): LuaTable {
    return LuaTable.listOf(
        map(transform).toTypedArray()
    )
}

fun <T> LuaTable.toList(valueTransform: (LuaValue) -> T): List<T> {
    val list = mutableListOf<T>()
    for (i in 1..this.length()) {
        list.add(valueTransform(this.get(i)))
    }
    return list
}