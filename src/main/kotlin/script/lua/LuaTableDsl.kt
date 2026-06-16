package org.lain.engine.script.lua

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction

fun luaTable(block: LuaTableBuilder.() -> Unit): LuaTable =
    LuaTableBuilder().apply(block).build()

class LuaTableBuilder {
    private val table = LuaTable()

    fun build(): LuaTable = table

    operator fun String.invoke(value: Any?) {
        table.set(this, value.toLua())
    }

    operator fun String.invoke(block: LuaTableBuilder.() -> Unit) {
        table.set(this, luaTable(block))
    }

    fun function(
        name: String,
        body: (Varargs) -> LuaValue
    ) {
        table.set(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return body(args)
            }
        })
    }

    fun function0(
        name: String,
        body: () -> LuaValue
    ) {
        table.set(name, zeroArgFunction(body))
    }

    fun function1(
        name: String,
        body: (LuaValue) -> LuaValue
    ) {
        table.set(name, oneArgFunction(body))
    }

    fun function2(
        name: String,
        body: (LuaValue, LuaValue) -> LuaValue
    ) {
        table.set(name, twoArgFunction(body))
    }

    fun function3(
        name: String,
        body: (LuaValue, LuaValue, LuaValue) -> LuaValue
    ) {
        table.set(name, threeArgFunction(body))
    }

    fun function4(
        name: String,
        body: (LuaValue, LuaValue, LuaValue, LuaValue) -> LuaValue
    ) {
        table.set(name, fourArgFunction(body))
    }

    fun functionV(
        name: String,
        body: (Varargs) -> Varargs
    ) {
        table.set(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return body(args)
            }
        })
    }

    fun method(
        name: String,
        body: LuaTable.(Varargs) -> LuaValue
    ) {
        table.set(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return body(table, args)
            }
        })
    }

    fun metatable(block: LuaTableBuilder.() -> Unit) {
        table.setmetatable(
            LuaTableBuilder().apply(block).build()
        )
    }

    fun metatable(meta: LuaTable) {
        table.setmetatable(meta)
    }

    fun index(block: LuaTableBuilder.() -> Unit) {
        table.set("__index", luaTable(block))
    }

    fun index(fn: (LuaValue, LuaValue) -> LuaValue) {
        table.set("__index", object : TwoArgFunction() {
            override fun call(a: LuaValue, b: LuaValue): LuaValue {
                return fn(a, b)
            }
        })
    }

    fun newIndex(fn: (LuaValue, LuaValue, LuaValue) -> Unit) {
        table.set("__newindex", object : ThreeArgFunction() {
            override fun call(
                a: LuaValue,
                b: LuaValue,
                c: LuaValue
            ): LuaValue {
                fn(a, b, c)
                return LuaValue.NIL
            }
        })
    }

    fun call(fn: (Varargs) -> Varargs) {
        table.set("__call", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return fn(args)
            }
        })
    }

    fun tostring(fn: (LuaValue) -> String) {
        table.set("__tostring", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                return LuaValue.valueOf(fn(arg))
            }
        })
    }
}

private fun Any?.toLua(): LuaValue = when (this) {
    null -> LuaValue.NIL
    is LuaValue -> this

    is String -> LuaValue.valueOf(this)
    is Int -> LuaValue.valueOf(this)
    is Double -> LuaValue.valueOf(this)
    is Float -> LuaValue.valueOf(this.toDouble())
    is Boolean -> LuaValue.valueOf(this)

    else -> LuaValue.userdataOf(this)
}