package org.lain.engine.script.lua

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.*
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.invokeCommand
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

context(lua: LuaContext)
fun PlayerMetaTable() = luaTable {
    function2("has_permission") { self, permission ->
        val player = self.asEnginePlayer()
        player.hasPermission(permission.tojstring()).toLuaValue()
    }
    function2("set_flying_speed") { self, speed ->
        val player = self.asEnginePlayer()
        player.setFlyingSpeed(speed.tofloat())
        NIL
    }
    function2("set_custom_max_speed") { self, speed ->
        val player = self.asEnginePlayer()
        player.setCustomMaxSpeed(speed.tofloat())
        NIL
    }
    function1("reset_custom_max_speed") { self ->
        val player = self.asEnginePlayer()
        player.resetCustomMaxSpeed()
        NIL
    }
    function2("narration") { self, narration ->
        val player = self.asEnginePlayer()
        player.serverNarration(
            narration.get("message").tojstring(),
            narration.get("time").toint(),
            narration.get("kick").nullable()?.toboolean() ?: false,
        )
        NIL
    }
    function3("invoke_command") { self, command, root ->
        val player = self.asEnginePlayer()
        val commandStr = command.tojstring()
        val rootBl = root.nullable()?.toboolean() ?: false
        player.invokeCommand(commandStr, rootBl)
        NIL
    }
}

fun LuaValue.asEnginePlayer() = this.checkuserdata() as EnginePlayer

context(context: LuaContext)
fun EnginePlayer.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    userdata.setmetatable(
        luaTable {
            index { self, key ->
                val player = self.asEnginePlayer()
                when(key.tojstring()) {
                    "uuid" -> player.id.value.toString().toLuaValue()
                    "id" -> player.entityId.toLuaValue()
                    "entity" -> with(player.world) { player.entityId.coerceToLua() }
                    "world" -> player.world.getLuaValue()
                    "is_spectating" -> player.isSpectating.toLuaValue()
                    "is_game_master" -> player.isInGameMasterMode.toLuaValue()
                    else -> context.playerMetaTable.get(key) ?: with(player.world) { player.entityId.coerceToLua().get(key) }
                }
            }
        }
    )
    return userdata
}