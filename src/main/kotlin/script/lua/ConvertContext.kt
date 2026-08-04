package org.lain.engine.script.lua

import org.lain.engine.player.extendArm
import org.lain.engine.player.interaction.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.player.isSpectating
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.script.lua.toLuaTable
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

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
    is ScriptContext.IntentExecution -> {
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

    is ScriptContext.ItemLoad -> luaTableOf(
        luaValue("world"), world.luaWorld(),
        luaValue("item"), with(world) { item.coerceToLua() },
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