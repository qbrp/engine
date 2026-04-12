package org.lain.engine.script.lua

import org.lain.engine.script.ScriptComponentType
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.VoxelPos
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.*

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

fun VoxelPos.toLuaValue(): LuaTable {
    return LuaValue.listOf(
        arrayOf(luaValue(x), luaValue(y), luaValue(z)),
    )
}

fun Vec3.toLuaValue(): LuaTable {
    return LuaValue.listOf(
        arrayOf(luaValue(x), luaValue(y), luaValue(z))
    )
}

fun LuaValue.toVoxelPos(): VoxelPos {
    val elements = checktable().toList { it.tofloat() }
    require(elements.size == 3) { "Invalid vector elements count: $elements" }
    return VoxelPos(elements[0], elements[1], elements[2])
}

fun LuaValue.toVector3f(): Vec3 {
    val elements = checktable().toList { it.tofloat() }
    require(elements.size == 3) { "Invalid vector elements count: $elements" }
    return Vec3(elements[0], elements[1], elements[2])
}

fun LuaValue.coerceToScriptComponentType(): ScriptComponentType {
    return checkuserdata(ScriptComponentType::class.java) as? ScriptComponentType ?: error("Invalid component type value")
}

fun ScriptComponentType.toLuaValue(): LuaValue {
    val userdata = LuaUserdata(this)
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> luaValue(ecsType.id)
                        else -> NIL
                    }
                }
            })
        }
    }
    userdata.setmetatable(meta)
    return userdata
}

fun zeroArgFunction(builder: () -> LuaValue) = object : ZeroArgFunction() {
    override fun call(): LuaValue {
        return builder.invoke()
    }
}

fun oneArgFunction(builder: (LuaValue) -> LuaValue) = object : OneArgFunction() {
    override fun call(arg: LuaValue): LuaValue {
        return builder.invoke(arg)
    }
}

fun twoArgFunction(builder: (LuaValue, LuaValue) -> LuaValue) = object : TwoArgFunction() {
    override fun call(arg: LuaValue, arg2: LuaValue): LuaValue {
        return builder.invoke(arg, arg2)
    }
}

fun threeArgFunction(builder: (LuaValue, LuaValue, LuaValue) -> LuaValue) = object : ThreeArgFunction() {
    override fun call(arg: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
        return builder.invoke(arg, arg2, arg3)
    }
}

fun fourArgFunction(builder: (LuaValue, LuaValue, LuaValue, LuaValue) -> LuaValue) = object : VarArgFunction() {
    override fun onInvoke(args: Varargs): Varargs {
        return builder.invoke(args.arg(1), args.arg(2), args.arg(3), args.arg(4))
    }
}

fun varargsFunction(builder: (Varargs) -> LuaValue) = object : VarArgFunction() {
    override fun onInvoke(args: Varargs): Varargs {
        return builder.invoke(args)
    }
}