package org.lain.engine.client.mc.sound

import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import org.lain.engine.mc.engineId
import org.lain.engine.util.math.ImmutableEVec3

class ServerSoundInstance(
    val engineId: Identifier,
    val engineVolume: Float,
    val enginePitch: Float,
    val soundSet: WeighedSoundEvents,
    val engineCategory: SoundSource,
    val pos: ImmutableEVec3,
    val static: Boolean = false
) : SoundInstance {
    private var random = SoundInstance.createUnseededRandom()
    private var sound: Sound? = null

    override fun getIdentifier(): Identifier = engineId

    override fun resolve(soundManager: SoundManager): WeighedSoundEvents {
        this.sound = soundSet.getSound(random)
        return soundSet
    }

    override fun getSound(): Sound {
        if (sound == null) {
            sound = soundSet.getSound(random)
        }
        return sound!!
    }

    override fun getSource(): SoundSource = engineCategory

    override fun isLooping(): Boolean = false

    override fun isRelative(): Boolean = static

    override fun getDelay(): Int = 0

    override fun getVolume(): Float = engineVolume * (sound?.volume?.sample(this.random) ?: 1f)

    override fun getPitch(): Float = enginePitch * (sound?.pitch?.sample(this.random) ?: 1f)

    override fun getX(): Double = pos.x.toDouble()

    override fun getY(): Double = pos.y.toDouble()

    override fun getZ(): Double = pos.z.toDouble()

    override fun getAttenuation(): SoundInstance.Attenuation = when(static) {
        true -> SoundInstance.Attenuation.NONE
        false -> SoundInstance.Attenuation.LINEAR
    }
}

class AudioSourceSoundInstance(
    private val engineId: Identifier,
    private val sound: Sound,
    private val engineCategory: SoundSource,
    var _isRelative: Boolean = false,
    var _x: Float = 0f,
    var _y: Float = 0f,
    var _z: Float = 0f,
    var _volume: Float = 1f,
    var _pitch: Float = 1f,
    var attenuate: Boolean = false
) : SoundInstance {
    override fun getIdentifier(): Identifier = engineId

    override fun resolve(soundManager: SoundManager): WeighedSoundEvents = SOUND_SET

    override fun getSound(): Sound = sound

    override fun getSource(): SoundSource = engineCategory

    override fun isLooping(): Boolean = false

    override fun isRelative(): Boolean = _isRelative

    override fun getDelay(): Int = 0

    override fun getVolume(): Float = _volume * sound.volume.sample(RANDOM)

    override fun getPitch(): Float = _pitch * sound.pitch.sample(RANDOM)

    override fun getX(): Double = _x.toDouble()

    override fun getY(): Double = _y.toDouble()

    override fun getZ(): Double = _z.toDouble()

    override fun getAttenuation(): SoundInstance.Attenuation = when(attenuate) {
        true -> SoundInstance.Attenuation.LINEAR
        false -> SoundInstance.Attenuation.NONE
    }

    companion object {
        private val RANDOM = SoundInstance.createUnseededRandom()
        private val SOUND_SET = WeighedSoundEvents(engineId("sound_set"), "engine")
    }
}
