package org.lain.engine.script.lua.library

import org.lain.engine.chat.hasPermission
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.displayNameString
import org.lain.engine.player.narration
import org.lain.engine.player.username
import org.lain.engine.script.lua.*
import org.lain.engine.world.invokeCommand
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

context(lua: LuaScriptEngine)
fun PlayerMetaTable() = luaUserdataTable<EnginePlayer> {
    functionSelf2("has_permission") { player, permission ->
        player.hasPermission(permission.tojstring()).luaBool()
    }
    functionSelf2("narration") { player, narration ->
        player.narration(
            narration.get("message").tojstring(),
            narration.get("time").toint(),
            narration.get("kick").nullable()?.toboolean() ?: false,
        )
        NIL
    }
    functionSelf3("invoke_command") { player, command, root ->
        val commandStr = command.tojstring()
        val rootBl = root.nullable()?.toboolean() ?: false
        player.invokeCommand(commandStr, rootBl)
        NIL
    }

    indexSelf { player, key ->
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
}

fun LuaValue.asEnginePlayer() = this.checkuserdata() as EnginePlayer

context(context: LuaScriptEngine)
fun EnginePlayer.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    userdata.setmetatable(context.playerMetaTable)
    return userdata
}
