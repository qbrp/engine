package org.lain.engine.script.lua

import org.lain.engine.script.SBool
import org.lain.engine.script.SInt
import org.lain.engine.script.SList
import org.lain.engine.script.SNil
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValue
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

fun LuaValue.toScriptValue(): ScriptValue = when(type()) {
    LuaValue.TNIL -> SNil
    LuaValue.TBOOLEAN -> SBool(toboolean())
    LuaValue.TINT -> SInt(toint())
    LuaValue.TNUMBER -> SNumber(todouble())
    LuaValue.TSTRING -> SString(tojstring())
    LuaValue.TTABLE -> {
        val table = checktable()
        val keys = table.keys()

        var isArray = true
        var maxIndex = 0

        for (key in keys) {
            if (!key.isint()) {
                isArray = false
                break
            }

            val index = key.toint()
            if (index <= 0) {
                isArray = false
                break
            }

            maxIndex = maxOf(maxIndex, index)
        }

        isArray = isArray && maxIndex == keys.size

        if (isArray) {
            SList(
                List(maxIndex) { index ->
                    table.get(index + 1).toScriptValue()
                }
            )
        } else {
            STable(
                buildMap(keys.size) {
                    for (key in keys) {
                        put(
                            key.toScriptValue(),
                            table.get(key).toScriptValue()
                        )
                    }
                }
            )
        }
    }
    else -> error("Unsupported Lua type: ${typename()}")
}

fun ScriptValue.toLuaValue(): LuaValue = when (this) {
    SNil -> LuaValue.NIL
    is SBool -> value.luaBool()
    is SNumber -> value.luaNum()
    is SString -> value.luaStr()
    is STable -> LuaTable.tableOf(
        map
            .toList()
            .flatMap { (k, v) -> listOf(k.toLuaValue(), v.toLuaValue()) }
            .toTypedArray()
    )
    is SInt -> value.luaNum()
    is SList -> values.toLuaList { it.toLuaValue() }
}