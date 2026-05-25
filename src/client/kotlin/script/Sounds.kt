package org.lain.engine.client.script

import org.lain.engine.client.util.AudioSource
import org.lain.engine.client.util.SoundParameters
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
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

context(ctx: ClientLuaContext)
fun AudioSource.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)

    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "x" -> luaValue(this@coerceToLua.x)
                        "y" -> luaValue(this@coerceToLua.y)
                        "z" -> luaValue(this@coerceToLua.z)

                        "volume" -> luaValue(this@coerceToLua.volume)
                        "pitch" -> luaValue(this@coerceToLua.pitch)

                        "is_relative" -> luaValue(this@coerceToLua.isRelative)
                        "attenuate" -> luaValue(this@coerceToLua.attenuate)
                        "is_ended" -> luaValue(this@coerceToLua.isEnded)

                        "radius" -> luaValue(this@coerceToLua.radius)

                        "slot" -> this@coerceToLua.slot
                            ?.let(::luaValue)
                            ?: NIL

                        "sound" -> CoerceJavaToLua.coerce(this@coerceToLua.sound)
                        "category" -> CoerceJavaToLua.coerce(this@coerceToLua.category)

                        else ->
                            ctx.audioSourceTable.get(key)
                                ?: rawget(key)
                    }
                }
            })

            set("__newindex", object : ThreeArgFunction() {
                override fun call(
                    self: LuaValue,
                    key: LuaValue,
                    value: LuaValue
                ): LuaValue {

                    when (key.tojstring()) {
                        "x" -> this@coerceToLua.x = value.tofloat()
                        "y" -> this@coerceToLua.y = value.tofloat()
                        "z" -> this@coerceToLua.z = value.tofloat()

                        "volume" -> this@coerceToLua.volume = value.tofloat()
                        "pitch" -> this@coerceToLua.pitch = value.tofloat()

                        "is_relative" ->
                            this@coerceToLua.isRelative = value.toboolean()

                        "attenuate" ->
                            this@coerceToLua.attenuate = value.toboolean()

                        "radius" ->
                            this@coerceToLua.radius = value.toint()

                        "slot" ->
                            this@coerceToLua.slot =
                                if (value.isnil()) null
                                else value.tojstring()

                        else -> rawset(key, value)
                    }

                    return NIL
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
        val toId: (LuaValue) -> SoundId = { SoundId(it.tojstring()) }
        val soundL = parameters.get("sound")
        val soundParameters = when(soundL.type()) {
            LuaValue.TTABLE -> SoundParameters(
                toId(soundL.get("id")),
                soundL.get("stream").toboolean(),
            )
            LuaValue.TSTRING -> SoundParameters(
                toId(soundL),
                false
            )
            else -> error("invalid sound type: ${soundL.type()}")
        }

        AudioSource(
            soundParameters,
            parameters.get("category").nullable()?.tojstring()?.lowercase()?.let { EngineSoundCategory.valueOf(it) } ?: EngineSoundCategory.AMBIENT,
            parameters.get("x").nullable()?.tofloat() ?: 0f,
            parameters.get("y").nullable()?.tofloat() ?: 0f,
            parameters.get("z").nullable()?.tofloat() ?: 0f,
            parameters.get("is_relative").nullable()?.toboolean() ?: true,
            parameters.get("volume")?.nullable()?.tofloat() ?: 1f,
            parameters.get("pitch")?.nullable()?.tofloat() ?: 1f,
            parameters.get("attenuate")?.nullable()?.toboolean() ?: false,
            radius = parameters.get("radius")?.nullable()?.toint() ?: 16
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