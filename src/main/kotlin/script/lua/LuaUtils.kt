package org.lain.engine.script.lua

import org.lain.engine.player.interaction.InputAction
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.util.AnyInputValue
import org.lain.engine.util.Input
import org.lain.engine.util.IntentSelection
import org.lain.engine.util.IntentTarget
import org.lain.engine.util.file.getBuiltinResource
import org.lain.engine.util.math.EVec3
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.math.asVec3
import org.lain.engine.world.VoxelPos
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.File
import kotlin.math.floor

fun File.writeDefaultLuaEntrypointScript() {
    writeText(getBuiltinResource("entrypoint.lua")?.readText() ?: "")
}

fun InputAction.toLuaTable() = when (this) {
    InputAction.Attack -> luaTable { "type"("attack") }
    InputAction.Base -> luaTable { "type"("base") }
    InputAction.TakeOff -> luaTable { "type"("take_off") }
}

fun LuaValue.nullable() = if (isnil()) null else this

class LuaFunctionChunk(function: String, vararg args: String) {
    private val str = "return function(${args.joinToString { it }}) $function end"

    fun getFunction(globals: Globals): LuaFunction {
        return globals.load(str).checkfunction()
    }

    fun execute(context: LuaScriptEngine, vararg args: LuaValue): LuaValue {
        return getFunction(context.globals).invoke(args.toList().toTypedArray()).arg1()
    }
}

fun List<AnyInputValue>.toLuaTable(): LuaTable {
    val table = LuaTable()
    forEach { table.set(luaValue(it.input.id), it.value.toLuaValue(it.input.type)) }
    return table
}

fun LuaValue.toKotlin(): Any? {
    return when (type()) {
        LuaValue.TNIL -> null
        LuaValue.TBOOLEAN -> toboolean()
        LuaValue.TINT -> toint()
        LuaValue.TSTRING -> tojstring()
        LuaValue.TFUNCTION -> { checkfunction().call() }
        LuaValue.TTABLE -> { checktable().toMap { it.toKotlin() } }
        else -> error("Invalid type: " + type())
    }
}

fun Any?.toLuaValue(type: Input.Type<*>): LuaValue {
    return when (type) {
        Input.Type.Logic -> (this as Boolean).luaBool()
        Input.Type.Integer -> (this as Int).luaNum()
        Input.Type.Double -> (this as Double).luaNum()
        Input.Type.Table -> TODO()
        is Input.Type.Text -> (this as String).luaStr()
    }
}

context(ctx: LuaScriptEngine)
fun IntentTarget.toLuaValue(): LuaTable = luaTableOf(
    luaValue("player"), player?.coerceToLua() ?: LuaValue.NIL,
    luaValue("voxel_pos"), voxelPos.toLuaValue(),
    luaValue("pos"), pos.asVec3().toLuaValue(),
)

fun IntentSelection.toLuaValue() = luaTableOf(
    luaValue("pos1"), pos1.toLuaValue(),
    luaValue("pos2"), pos2.toLuaValue(),
)

fun VoxelPos.toLuaValue() = luaListOf(x, y, z)

fun EVec3.toLuaValue() = luaListOf(x.toDouble(), y.toDouble(), z.toDouble())

fun LuaValue.toVoxelPos(): VoxelPos {
    val elements = checktable().toList { it.tofloat() }
    require(elements.size == 3) { "Invalid vector elements count: $elements" }
    return VoxelPos(
        floor(elements[0]),
        floor(elements[1]),
        floor(elements[2])
    )
}

fun LuaValue.toVector3f(): EVec3 {
    val elements = checktable().toList { it.tofloat() }
    require(elements.size == 3) { "Invalid vector elements count: $elements" }
    return Vec3(elements[0], elements[1], elements[2])
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
