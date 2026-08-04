package org.lain.engine.script.lua

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ThreeArgFunction

class LuaDataStorage {
    private data class DataKey(val module: String, val id: String)
    private val values: MutableMap<DataKey, LuaValue> = mutableMapOf()

    fun get(module: String, slot: String) = values[DataKey(module, slot)]

    fun set(module: String, id: String, value: LuaValue) {
        values[DataKey(module, id)] = value
    }

    fun getOrDefault(module: String, id: String, default: LuaValue): LuaValue {
        return values.computeIfAbsent(DataKey(module, id)) { default }
    }

    fun setup(globals: Globals) {
        globals.set("remember", threeArgFunction { arg1, arg2, arg3 ->
            getOrDefault(arg3.nullable()?.tojstring() ?: "global", arg2.tojstring(), arg1)
        })
    }
}