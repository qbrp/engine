package org.lain.engine.script.lua

import org.lain.engine.player.*
import org.lain.engine.world.world
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction

fun LuaValue.coerceToEnginePlayer() = this.checkuserdata() as EnginePlayer

context(context: LuaContext)
fun EnginePlayer.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> luaValue(this@coerceToLua.id.toString())
                        "entity_id" -> luaValue(this@coerceToLua.entityId)
                        "world" -> this@coerceToLua.world.coerceToLua()
                        else -> context.playerTable.get(key)
                    }
                }
            })
        }
    }

    userdata.setmetatable(meta)
    return userdata
}

context(ctx: LuaContext)
fun Globals.setupPlayer() {
    ctx.playerTable.set("_has_permission", twoArgFunction { self, permission ->
        val player = self.coerceToEnginePlayer()
        player.hasPermission(permission.tojstring()).toLuaValue()
    })

    ctx.playerTable.set("_is_game_master", object : OneArgFunction() {
        override fun call(self: LuaValue): LuaValue {
            val player = self.coerceToEnginePlayer()
            return luaValue(player.isInGameMasterMode)
        }
    })

    ctx.playerTable.set("_set_custom_max_speed", object : TwoArgFunction() {
        override fun call(self: LuaValue, arg2: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            val speed = arg2.tofloat()
            player.setCustomMaxSpeed(speed)
            return NIL
        }
    })

    ctx.playerTable.set("_reset_custom_max_speed", object : OneArgFunction() {
        override fun call(self: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            player.resetCustomMaxSpeed()
            return NIL
        }
    })

    ctx.playerTable.set("_narration", object : TwoArgFunction() {
        override fun call(self: LuaValue, arg2: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            val narration = arg2.checktable()
            player.serverNarration(
                narration.get("message").tojstring(),
                narration.get("time").toint(),
                narration.get("kick").nullable()?.toboolean() ?: false,
            )
            return NIL
        }
    })
}