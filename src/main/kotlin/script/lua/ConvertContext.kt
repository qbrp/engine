package org.lain.engine.script.lua

import org.lain.engine.player.extendArm
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.player.interaction.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.player.isSpectating
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.util.AnyInputValue
import org.lain.engine.util.Input
import org.lain.engine.util.OperationSelection
import org.lain.engine.util.OperationTarget
import org.lain.engine.util.math.asMutableVec3
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import kotlin.collections.forEach

/**
 * Конвертировать ScriptContext в таблицу.
 * Здесь не обрабатывается конвертация ScriptContext.SystemEntityHandle, т.к. она требует передачи нескольких
 * аргументов для вызова фунгции.
 * @see LuaScript
 */
context(ctx: LuaScriptEngine)
internal fun ScriptContext.toLuaValue(): LuaValue = when(this) {
    is ScriptContext.Player -> {
        player.coerceToLua()
    }
    is ScriptContext.World -> {
        world.luaWorld()
    }
    is ScriptContext.VoxelAction -> {
        luaTableOf(
            luaValue("player"), player?.coerceToLua() ?: LuaValue.NIL,
            luaValue("world"), world.luaWorld(),
            luaValue("voxel_pos"), pos.toLuaValue(),
            luaValue("voxel_meta"), meta.coerceToLua(),
        )
    }
    is ScriptContext.OperationExecution -> {
        val (actor, target, inputs, behaviour) = this
        luaTableOf(
            luaValue("world"), actor.player.world.luaWorld(),
            luaValue("actor"), luaTableOf(
                luaValue("type"), actor.type.name.lowercase().luaStr(),
                luaValue("player"), actor.player.coerceToLua(),
                luaValue("entity"), actor.entity.luaNum(),
            ),
            luaValue("target"), target?.toLuaValue() ?: LuaValue.NIL,
            luaValue("inputs"), inputs.toLuaTable(),
            luaValue("gen_target"), zeroArgFunction { behaviour.generateTarget().toLuaValue() },
            luaValue("gen_selection"), zeroArgFunction { behaviour.generateSelection()?.toLuaValue() ?: LuaValue.NIL },
            luaValue("feedback"), oneArgFunction {
                behaviour.feedback(it.tojstring())
                LuaValue.NIL
            }
        )
    }

    is ScriptContext.Item -> luaTableOf(
        luaValue("world"), world.luaWorld(),
        luaValue("item"), with(world) { item.luaEntity() },
    )

    is ScriptContext.PlayerInputTick -> luaTable {
        val input = input
        "player"(player.coerceToLua())
        "actions"(input.actions.toLuaList { it.toLuaTable() })
        "last_actions"(input.lastActions.toLuaList { it.toLuaTable() })
        "is_spectating"(player.isSpectating)
        "social_interaction_distance"(SOCIAL_INTERACTION_DISTANCE)
        "extend_arm"(player.extendArm)
    }

    else -> error("Контекст скрипта $this не может быть использован на стороне сервера")
}

fun InputAction.toLuaTable() = when (this) {
    InputAction.Attack -> luaTable { "type"("attack") }
    InputAction.Base -> luaTable { "type"("base") }
    InputAction.TakeOff -> luaTable { "type"("take_off") }
}

context(ctx: LuaScriptEngine)
fun OperationTarget.toLuaValue(): LuaTable = luaTableOf(
    luaValue("player"), player?.coerceToLua() ?: LuaValue.NIL,
    luaValue("voxel_pos"), voxelPos.toLuaValue(),
    luaValue("pos"), pos.asMutableVec3().coerceToLua(),
)

fun OperationSelection.toLuaValue() = luaTableOf(
    luaValue("pos1"), pos1.toLuaValue(),
    luaValue("pos2"), pos2.toLuaValue(),
)

private fun Any?.toInputLuaValue(type: Input.Type<*>): LuaValue {
    return when (type) {
        Input.Type.Logic -> (this as Boolean).luaBool()
        Input.Type.Integer -> (this as Int).luaNum()
        Input.Type.Double -> (this as Double).luaNum()
        Input.Type.Table -> TODO()
        is Input.Type.Text -> (this as String).luaStr()
    }
}

fun List<AnyInputValue>.toLuaTable(): LuaTable {
    val table = LuaTable()
    forEach { table.set(luaValue(it.input.id), it.value.toInputLuaValue(it.input.type)) }
    return table
}
