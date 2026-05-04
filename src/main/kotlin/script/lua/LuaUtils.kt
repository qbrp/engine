package org.lain.engine.script.lua

import kotlinx.serialization.json.*
import org.lain.engine.storage.LOGGER
import org.lain.engine.util.AnyInputValue
import org.lain.engine.util.Input
import org.lain.engine.util.IntentSelection
import org.lain.engine.util.IntentTarget
import org.lain.engine.util.file.getBuiltinResource
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.math.asVec3
import org.lain.engine.world.VoxelPos
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.*
import java.io.File
import java.util.*
import kotlin.math.floor

fun File.writeDefaultLuaEntrypointScript() {
    writeText(getBuiltinResource("entrypoint.lua")?.readText() ?: "")
}

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

fun Any?.toLuaValue(type: Input.Type<*>): LuaValue {
    return when (type) {
        Input.Type.Logic -> (this as Boolean).toLuaValue()
        Input.Type.Integer -> (this as Int).toLuaValue()
        Input.Type.Double -> (this as Double).toLuaValue()
        Input.Type.Table -> TODO()
        is Input.Type.Text -> (this as String).toLuaValue()
    }
}

context(ctx: LuaContext)
fun IntentTarget.toLuaValue(): LuaTable = luaTableOf(
    luaValue("player"), player?.coerceToLua() ?: LuaValue.NIL,
    luaValue("voxel_pos"), voxelPos.toLuaValue(),
    luaValue("pos"), pos.asVec3().toLuaValue(),
)

fun IntentSelection.toLuaValue() = luaTableOf(
    luaValue("pos1"), pos1.toLuaValue(),
    luaValue("pos2"), pos2.toLuaValue(),
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
    return VoxelPos(
        floor(elements[0]),
        floor(elements[1]),
        floor(elements[2])
    )
}

fun LuaValue.toVector3f(): Vec3 {
    val elements = checktable().toList { it.tofloat() }
    require(elements.size == 3) { "Invalid vector elements count: $elements" }
    return Vec3(elements[0], elements[1], elements[2])
}

fun LuaValue.coerceToScriptComponentType(): LazyScriptComponentType {
    return checkuserdata(LazyScriptComponentType::class.java) as? LazyScriptComponentType ?: error("Invalid component type value")
}

fun LazyScriptComponentType.toLuaValue(): LuaValue {
    val userdata = LuaUserdata(this)

    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> { luaValue(requireComponent().id) }
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

private val visited = Collections.newSetFromMap(IdentityHashMap<LuaValue, Boolean>())

fun LuaValue.toJsonDeep(): JsonElement {
    if (!visited.add(this)) {
        return JsonNull
    }
    return when (val type = typename()) {
        "nil" -> JsonNull
        "string" -> JsonPrimitive(tojstring())
        "number" -> JsonPrimitive(todouble())
        "boolean" -> JsonPrimitive(toboolean())
        "table" -> {
            val table = checktable()
            val keys = table.keys().toList()
            val intKeys = keys.mapNotNull {
                val s = it.tojstring()
                s.toIntOrNull()
            }

            val isArray = intKeys.isNotEmpty() &&
                        intKeys.minOrNull() == 1 &&
                        intKeys.maxOrNull() == intKeys.size &&
                        intKeys.size == keys.size

            if (isArray) {
                val arr = JsonArray(
                    (1..intKeys.size).map { i ->
                        table.get(i)?.toJsonDeep() ?: JsonNull
                    }
                )
                arr
            } else {
                val obj = buildMap<String, JsonElement> {
                    for (k in keys) {
                        val v = table.get(k)
                        put(k.tojstring(), v?.toJsonDeep() ?: JsonNull)
                    }
                }
                JsonObject(obj)
            }
        }
        else -> {
            val userdata = this.touserdata()?.let { it::class }
            val errorStr = StringBuilder()
            errorStr.append("Lua value (")
            if (userdata != null) {
                errorStr.append(userdata.qualifiedName)
            } else {
                errorStr.append(this)
            }
            errorStr.append(") serialization exception: unsupported type $type")
            LOGGER.error(errorStr.toString())
            JsonNull
        }
    }.also {
        visited.remove(this)
    }
}

fun JsonElement.toLuaValue(): LuaValue {
    return when (this) {
        is JsonNull -> LuaValue.NIL
        is JsonPrimitive -> {
            when {
                isString -> LuaValue.valueOf(content)
                booleanOrNull != null -> LuaValue.valueOf(boolean)
                else -> content.toDoubleOrNull()?.toLuaValue() ?: error("Invalid number: $content")
            }
        }
        is JsonArray -> {
            val table = LuaValue.tableOf()
            this.forEachIndexed { index, element ->
                table.set(index + 1, element.toLuaValue())
            }
            table
        }
        is JsonObject -> {
            val table = LuaValue.tableOf()
            for ((key, value) in this) {
                table.set(key, value.toLuaValue())
            }
            table
        }
    }
}

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

fun varargsFunction(builder: (Varargs) -> LuaValue?) = object : VarArgFunction() {
    override fun onInvoke(args: Varargs): Varargs {
        return builder.invoke(args) ?: LuaValue.NIL
    }
}