package org.lain.engine.client.script

import org.lain.engine.client.mc.sound.MinecraftAudioManager
import org.lain.engine.client.util.AudioSource
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.client.util.SoundParameters
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.luaStr
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
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

class AudioLibrary(
    private val audioManager: EngineAudioManager
) {
    var lastAudioSlotId = 0
    private val audioSource = LuaUserdataType<AudioSource> {
        functionSelf("stop") { self ->
            audioManager.stopAudioSource(self)
            NIL
        }

        functionSelf("play") { self ->
            audioManager.addAudioSource(
                self,
                lastAudioSlotId++.toString()
            )
            NIL
        }

        indexSelf { self, key ->
            when (key.tojstring()) {
                "x" -> self.x.toDouble().luaNum()
                "y" -> self.y.toDouble().luaNum()
                "z" -> self.z.toDouble().luaNum()

                "volume" -> self.volume.toDouble().luaNum()
                "pitch" -> self.pitch.toDouble().luaNum()

                "spatial" -> luaValue(self.spatial)
                "ended" -> luaValue(self.isEnded)

                "radius" -> luaValue(self.radius)

                "slot" -> self.slot?.luaStr() ?: NIL

                "looping" -> self.looping.luaBool()

                "sound" -> CoerceJavaToLua.coerce(self.sound)
                "category" -> CoerceJavaToLua.coerce(self.category)
                else -> NIL
            }
        }
    }

    val library = luaTable {
        function1("create") { parametersL ->
            fun LuaValue.toSoundId() = SoundId(this.tojstring())

            val soundL = parametersL.get("sound")
            val soundParameters = when (soundL.type()) {
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

            audioSource.newInstance(
                AudioSource(
                    soundParameters,
                    parametersL["category"].nullable()?.tojstring()?.lowercase()
                        ?.let { EngineSoundCategory.valueOf(it) } ?: EngineSoundCategory.AMBIENT,
                    parametersL["x"].nullable()?.tofloat() ?: 0f,
                    parametersL["y"].nullable()?.tofloat() ?: 0f,
                    parametersL["z"].nullable()?.tofloat() ?: 0f,
                    parametersL["volume"]?.nullable()?.tofloat() ?: 1f,
                    parametersL["pitch"]?.nullable()?.tofloat() ?: 1f,
                    parametersL["spatial"]?.nullable()?.toboolean() ?: false,
                    parametersL["radius"]?.nullable()?.toint() ?: 16,
                    parametersL["looping"]?.nullable()?.toboolean() ?: false
                )
            )
        }
    }
}