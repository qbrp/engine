package org.lain.engine.script

import org.lain.engine.util.math.Vec3
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

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