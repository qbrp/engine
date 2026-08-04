package org.lain.engine.client.script

import org.lain.engine.client.util.AudioSource
import org.lain.engine.client.util.SoundParameters
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.oneArgFunction
import org.lain.engine.world.EngineSoundCategory
import org.lain.engine.world.SoundId
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

context(ctx: ClientLuaScriptEngine)
fun AudioSource.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)

    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "x" -> x.toDouble().luaNum()
                        "y" -> y.toDouble().luaNum()
                        "z" -> z.toDouble().luaNum()

                        "volume" -> volume.toDouble().luaNum()
                        "pitch" -> pitch.toDouble().luaNum()

                        "spatial" -> luaValue(this@coerceToLua.spatial)
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

                        "spatial" -> this@coerceToLua.spatial = value.toboolean()
                        "radius" -> this@coerceToLua.radius = value.toint()

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

context(ctx: ClientLuaScriptEngine)
fun Globals.setupAudio() = luaTable {
    val audioManager = ctx.client.audioManager

    function1("create") { parametersL ->
        fun LuaValue.toSoundId() = SoundId(this.tojstring())

        val soundL = parametersL.get("sound")
        val soundParameters = when(soundL.type()) {
            LuaValue.TTABLE -> SoundParameters(
                soundL.get("id").toSoundId(),
                soundL.get("stream").toboolean(),
            )
            LuaValue.TSTRING -> SoundParameters(
                soundL.toSoundId(),
                false
            )
            else -> error("invalid sound type: ${soundL.type()}")
        }

        AudioSource(
            soundParameters,
            parametersL.get("category").nullable()?.tojstring()?.lowercase()?.let { EngineSoundCategory.valueOf(it) } ?: EngineSoundCategory.AMBIENT,
            parametersL.get("x").nullable()?.tofloat() ?: 0f,
            parametersL.get("y").nullable()?.tofloat() ?: 0f,
            parametersL.get("z").nullable()?.tofloat() ?: 0f,
            parametersL.get("volume")?.nullable()?.tofloat() ?: 1f,
            parametersL.get("pitch")?.nullable()?.tofloat() ?: 1f,
            parametersL.get("spatial")?.nullable()?.toboolean() ?: false,
            parametersL.get("radius")?.nullable()?.toint() ?: 16
        ).coerceToLua()
    }

    function1("play") { self ->
        val audioSource = self.coerceToEngineAudioSource()
        audioManager.addAudioSource(audioSource, ctx.lastAudioSlotId++.toString())
        LuaValue.NIL
    }

    ctx.audioSourceTable.set("stop", oneArgFunction { self ->
        audioManager.stopAudioSource(self.coerceToEngineAudioSource())
        LuaValue.NIL
    })
}