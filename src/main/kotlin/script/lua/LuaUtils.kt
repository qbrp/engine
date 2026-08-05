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

class LuaFunctionChunk(function: String, vararg args: String) {
    private val str = "return function(${args.joinToString { it }}) $function end"

    fun getFunction(globals: Globals): LuaFunction {
        return globals.load(str).checkfunction()
    }

    fun execute(context: LuaScriptEngine, vararg args: LuaValue): LuaValue {
        return getFunction(context.globals).invoke(args.toList().toTypedArray()).arg1()
    }
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

fun VoxelPos.toLuaValue() = luaListOf(x, y, z)

fun LuaValue.toVoxelPos(): VoxelPos {
    val elements = checktable().toList { it.tofloat() }
    require(elements.size == 3) { "Invalid vector elements count: $elements" }
    return VoxelPos(
        floor(elements[0]),
        floor(elements[1]),
        floor(elements[2])
    )
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
