package org.lain.engine.script.lua

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

fun zeroArgFunction(builder: () -> LuaValue) = object : ZeroArgFunction() {
    override fun call(): LuaValue {
        return builder.invoke() ?: LuaValue.NIL
    }
}

fun oneArgFunction(builder: (LuaValue) -> LuaValue?) = object : OneArgFunction() {
    override fun call(arg: LuaValue): LuaValue {
        return builder.invoke(arg) ?: LuaValue.NIL
    }
}

fun twoArgFunction(builder: (LuaValue, LuaValue) -> LuaValue?) = object : TwoArgFunction() {
    override fun call(arg: LuaValue, arg2: LuaValue): LuaValue {
        return builder.invoke(arg, arg2) ?: LuaValue.NIL
    }
}

fun threeArgFunction(builder: (LuaValue, LuaValue, LuaValue) -> LuaValue?) = object : ThreeArgFunction() {
    override fun call(arg: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
        return builder.invoke(arg, arg2, arg3) ?: LuaValue.NIL
    }
}

fun fourArgFunction(builder: (LuaValue, LuaValue, LuaValue, LuaValue) -> LuaValue?) = object : VarArgFunction() {
    override fun onInvoke(args: Varargs): Varargs {
        return builder.invoke(args.arg(1), args.arg(2), args.arg(3), args.arg(4)) ?: LuaValue.NIL
    }
}

fun varargsFunction(builder: (Varargs) -> Varargs?) = object : VarArgFunction() {
    override fun onInvoke(args: Varargs): Varargs {
        return builder.invoke(args) ?: LuaValue.NIL
    }
}

fun luaTableOf(vararg values: LuaValue): LuaTable {
    return LuaTable.tableOf(values)
}

fun emptyLuaTable(): LuaTable = LuaTable.tableOf()

fun luaListOf(vararg values: LuaValue): LuaTable {
    return LuaTable.listOf(values)
}

fun luaListOf(vararg nums: Double): LuaTable {
    return LuaTable.listOf(nums.map { it.luaNum() }.toTypedArray())
}

fun luaListOf(vararg nums: Int): LuaTable {
    return LuaTable.listOf(nums.map { it.luaNum() }.toTypedArray())
}

fun LuaValue.nullable() = if (isnil()) null else this