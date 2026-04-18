package org.lain.engine.script.lua

import org.lain.engine.script.ScriptComponentType
import org.lain.engine.util.AnyInputValue
import org.lain.engine.util.Input
import org.lain.engine.util.IntentTarget
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.math.asVec3
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

fun String.toLuaValue(): LuaValue = luaValue(this)

fun luaValueNullable(string: String?) = LuaValue.valueOf(string) ?: LuaValue.NIL

fun luaValue(int: Int) = LuaValue.valueOf(int)

fun Int.toLuaValue(): LuaValue = luaValue(this)

fun luaValue(float: Float) = LuaValue.valueOf(float.toDouble())

fun Float.toLuaValue(): LuaValue = luaValue(this)

fun luaValue(boolean: Boolean) = LuaValue.valueOf(boolean)

fun Boolean.toLuaValue(): LuaValue = luaValue(this)

fun luaValue(double: Double) = LuaValue.valueOf(double)

fun Double.toLuaValue(): LuaValue = luaValue(this)

fun List<AnyInputValue>.toLuaTable(): LuaTable {
    val table = LuaTable()
    forEach { table.set(luaValue(it.input.id), it.value.toLuaValue(it.input.type)) }
    return table
}

private fun Any?.toLuaValue(type: Input.Type<*>): LuaValue {
    return when (type) {
        Input.Type.Logic -> (this as Boolean).toLuaValue()
        Input.Type.Integer -> (this as Int).toLuaValue()
        Input.Type.Double -> (this as Double).toLuaValue()
        Input.Type.Table -> (this as List<AnyInputValue>).toLuaTable()
        is Input.Type.Text -> (this as String).toLuaValue()
    }
}

context(ctx: LuaContext)
fun IntentTarget.toLuaValue(): LuaTable = luaTableOf(
    luaValue("player"), player?.coerceToLua() ?: LuaValue.NIL,
    luaValue("voxel_pos"), voxelPos.toLuaValue(),
    luaValue("pos"), pos.asVec3().toLuaValue(),
)

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

fun LuaTable.toIntentInput(): Input<out Any> {
    val id = get("id").tojstring()
    val type = when(val type = get("type").tojstring()) {
        "text" -> Input.Type.Text(false)
        "int" -> Input.Type.Integer
        "double" -> Input.Type.Double
        "logic" -> Input.Type.Logic
        "table" -> Input.Type.Table
        else -> error("Unsupported table type $type")
    }
    return Input(id, type)
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