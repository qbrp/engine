package org.lain.engine.script.lua

import org.lain.engine.player.extendArm
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.player.interaction.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.player.isSpectating
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.script.InputValue
import org.lain.engine.script.OperationSelection
import org.lain.engine.script.OperationTarget
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
    is ScriptContext.OperationInputResolution -> {
        val (actor, operationId, inputId, inputs) = this
        luaTableOf(
            luaValue("world"), actor.player.world.luaWorld(),
            luaValue("actor"), luaTableOf(
                luaValue("type"), actor.type.name.lowercase().luaStr(),
                luaValue("player"), actor.player.coerceToLua(),
                luaValue("entity"), actor.entity.luaNum(),
            ),
            luaValue("operation_id"), operationId.toString().luaStr(),
            luaValue("input_id"), inputId.luaStr(),
            luaValue("inputs"), inputs.toLuaTable(),
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

    is ScriptContext.ComponentMigration -> component.toLuaValue()

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

context(ctx: LuaScriptEngine)
fun List<InputValue>.toLuaTable(): LuaTable {
    val table = LuaTable()
    forEach { table.set(luaValue(it.id), it.value.toLuaValue()) }
    return table
}
