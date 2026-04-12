package org.lain.engine.client.script

import org.lain.engine.client.util.AudioSource
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.oneArgFunction
import org.lain.engine.script.lua.twoArgFunction
import org.lain.engine.world.EngineSoundCategory
import org.lain.engine.world.SoundId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

context(ctx: ClientLuaContext)
fun AudioSource.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    val coercion = CoerceJavaToLua.coerce(this)
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when(key.tojstring()) {
                        "is_ended" -> luaValue(isEnded)
                        else -> coercion.get(key).nullable()
                            ?: ctx.audioSourceTable.get(key)
                            ?: rawget(key) // важно!
                    }
                }
            })
        }
    }
    userdata.setmetatable(meta)
    return userdata
}

fun LuaValue.coerceToEngineAudioSource() = this.checkuserdata() as AudioSource

context(ctx: ClientLuaContext)
fun Globals.setupAudio() {
    val audioManager = ctx.client.audioManager
    ctx.audioSourceTable.set("_create", oneArgFunction { parameters ->
        AudioSource(
            SoundId(parameters.get("sound").tojstring()),
            parameters.get("category").nullable()?.tojstring()?.lowercase()?.let { EngineSoundCategory.valueOf(it) } ?: EngineSoundCategory.AMBIENT,
            parameters.get("x").nullable()?.tofloat() ?: 0f,
            parameters.get("y").nullable()?.tofloat() ?: 0f,
            parameters.get("z").nullable()?.tofloat() ?: 0f,
            parameters.get("is_relative").nullable()?.toboolean() ?: true,
            parameters.get("volume")?.nullable()?.tofloat() ?: 1f,
            parameters.get("pitch")?.nullable()?.tofloat() ?: 1f,
            parameters.get("attenuate")?.nullable()?.toboolean() ?: false,
        ).coerceToLua()
    })
    ctx.audioSourceTable.set("_play", twoArgFunction { self, slotId ->
        val audioSource = self.coerceToEngineAudioSource()
        audioManager.addAudioSource(audioSource, slotId.tojstring())
        LuaValue.NIL
    })

    ctx.audioSourceTable.set("_stop", oneArgFunction { self ->
        audioManager.stopAudioSource(self.coerceToEngineAudioSource())
        LuaValue.NIL
    })
}