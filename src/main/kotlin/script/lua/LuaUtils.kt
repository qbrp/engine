package org.lain.engine.script.lua

import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptSource
import org.lain.engine.script.Input
import org.lain.engine.script.InputType
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.SelectionVariants
import org.lain.engine.script.toSelectionEntries
import org.lain.engine.util.file.FileSystem
import org.lain.engine.world.VoxelPos
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import java.io.File
import kotlin.math.floor

fun File.writeDefaultLuaEntrypointScript() {
    writeText(FileSystem.builtinResource(FileSystem.COMPILATION_ENTRYPOINT_NAME)?.readText() ?: "")
}

fun LuaValue.toEngineId() = EngineId(tojstring())

class LuaType<T, M : LuaValue> internal constructor(
    val metaTable: LuaTable,
    private val constructor: (T) -> M
) {
    fun newInstance(component: T): M = constructor(component)
        .apply { setmetatable(metaTable) }
}

fun <T> LuaUserdataType(builder: UserdataLuaTableBuilder<T>.() -> Unit): LuaType<T, LuaUserdata> {
    return LuaType(
        UserdataLuaTableBuilder<T>().apply(builder).build(),
        { LuaUserdata(it) }
    )
}

fun LuaTableType(builder: LuaTableBuilder.() -> Unit): LuaType<LuaTable, LuaTable> {
    return LuaType(
        LuaTableBuilder().apply(builder).build(),
        { LuaTable() }
    )
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

context(lua: LuaScriptEngine)
fun LuaTable.toOperationInput(): Input {
    val id = get("id").tojstring()
    val type = when(val type = get("type").tojstring()) {
        "text" -> InputType.Text(
            get("single_word").nullable()?.toboolean()
                ?: get("is_single_word").nullable()?.toboolean()
                ?: false
        )
        "int", "integer" -> InputType.Integer(
            get("min").nullable()?.checkint(),
            get("max").nullable()?.checkint(),
        )
        "double" -> InputType.Double(
            get("min").nullable()?.checkdouble(),
            get("max").nullable()?.checkdouble(),
        )
        "logic" -> InputType.Logic
        "table" -> InputType.Table
        "selection" -> InputType.Selection(
            when (val variants = get("variants")) {
                is LuaTable -> SelectionVariants.Static(
                    variants.toScriptValue().toSelectionEntries()
                )
                is LuaFunction -> SelectionVariants.Dynamic(
                    LuaScript(lua, variants)
                )
                else -> error("Selection variants must be a table or function")
            }
        )
        else -> error("Unsupported table type $type")
    }
    return Input(id, type)
}

fun Globals.loadScript(source: ScriptSource): LuaValue = source.open().use {
    load(it.reader(), source.chunkName)
}
