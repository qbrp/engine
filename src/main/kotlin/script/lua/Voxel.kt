package org.lain.engine.script.lua

import org.lain.engine.world.VoxelMeta
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction

fun VoxelMeta.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (val k = key.tojstring()) {
                        "id" -> luaValue(this@coerceToLua.id)
                        "has_tag" -> object : OneArgFunction() {
                            override fun call(tag: LuaValue): LuaValue {
                                return luaValue(this@coerceToLua.hasTag(tag.tojstring()))
                            }
                        }
                        else -> NIL
                    }
                }
            })
        }
    }

    userdata.setmetatable(meta)
    return userdata
}