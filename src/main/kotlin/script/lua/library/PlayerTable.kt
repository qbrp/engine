package org.lain.engine.script.lua.library

import org.lain.engine.chat.hasPermission
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.displayNameString
import org.lain.engine.player.serverNarration
import org.lain.engine.player.username
import org.lain.engine.script.lua.*
import org.lain.engine.world.invokeCommand
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

context(lua: LuaScriptEngine)
fun PlayerMetaTable() = luaTable {
    index { self, key ->
        val player = self.asEnginePlayer()
        when (key.tojstring()) {
            "uuid" -> player.id.value.toString().luaStr()
            "id" -> player.entity.luaNum()
            "entity" -> with(player.world) { player.entity.luaEntity() }
            "world" -> player.world.luaWorld()
            "display_name" -> player.displayNameString.luaStr()
            "user_name" -> player.username.luaStr()
            else -> with(player.world) {
                val entity = player.entity.luaEntity()
                val method = entity.get(key)

                if (!method.isfunction()) {
                    method
                } else {
                    varargsFunction { args ->
                        method.invoke(
                            LuaValue.varargsOf(entity, args.subargs(2))
                        )
                    }
                }
            }
        }
    }

    function2("has_permission") { self, permission ->
        val player = self.asEnginePlayer()
        player.hasPermission(permission.tojstring()).luaBool()
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

context(context: LuaScriptEngine)
fun EnginePlayer.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    userdata.setmetatable(context.playerMetaTable)
    return userdata
}
