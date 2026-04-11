package org.lain.engine.client.mc.sound

import net.minecraft.client.sound.*
import net.minecraft.client.sound.SoundInstance.AttenuationType
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.random.Random
import org.lain.engine.player.Hearing
import org.lain.engine.player.TINNITUS_HEAR_THRESHOLD
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.util.math.lerp

class TinnitusSoundInstance(private val hearing: Hearing) : AbstractSoundInstance(EVENT, SoundCategory.AMBIENT, Random.create()), TickableSoundInstance {
    private var ticks = 0
    private val loss
        get() = (hearing.loss - TINNITUS_HEAR_THRESHOLD) / (1f - TINNITUS_HEAR_THRESHOLD)
    private val targetVolume
        get() = 0.1f + loss

    init { volume = targetVolume }

    override fun isDone(): Boolean {
        return volume < 0.01 && hearing.loss < 0.01
    }

    override fun tick() {
        ticks++
        val loss = hearing.loss
        if (loss > 0.05f) {
            this.volume = lerp(volume, targetVolume, 0.5f)
        } else {
            this.volume = lerp(volume, 0f, 0.05f)
        }
    }

    override fun shouldAlwaysPlay(): Boolean {
        return true
    }

    companion object {
        val EVENT = registerSoundEvent("tinnitus")

        // lazy
        fun registerEvents() {}
    }
}

class ServerSoundInstance(
    val engineId: Identifier,
    val engineVolume: Float,
    val enginePitch: Float,
    val soundSet: WeightedSoundSet,
    val engineCategory: SoundCategory,
    val pos: ImmutableVec3,
    val static: Boolean = false
) : SoundInstance {
    private var random = SoundInstance.createRandom()
    private var sound: Sound? = null

    override fun getId(): Identifier = engineId

    override fun getSoundSet(soundManager: SoundManager): WeightedSoundSet {
        this.sound = soundSet.getSound(random)
        return soundSet
    }

    override fun getSound(): Sound {
        if (sound == null) {
            sound = soundSet.getSound(random)
        }
        return sound!!
    }

    override fun getCategory(): SoundCategory = engineCategory

    override fun isRepeatable(): Boolean = false

    override fun isRelative(): Boolean = static

    override fun getRepeatDelay(): Int = 0

    override fun getVolume(): Float = engineVolume * (sound?.volume?.get(this.random) ?: 1f)

    override fun getPitch(): Float = enginePitch * (sound?.pitch?.get(this.random) ?: 1f)

    override fun getX(): Double = pos.x.toDouble()

    override fun getY(): Double = pos.y.toDouble()

    override fun getZ(): Double = pos.z.toDouble()

    override fun getAttenuationType(): AttenuationType = when(static) {
        true -> AttenuationType.NONE
        false -> AttenuationType.LINEAR
    }
}

class AudioSourceSoundInstance(
    private val engineId: Identifier,
    private val sound: Sound,
    private val engineCategory: SoundCategory,
    var isRelative: Boolean = false,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var volume: Float = 0f,
    var pitch: Float = 0f,
    var attenuate: Boolean = false
) : SoundInstance {
    override fun getId(): Identifier = engineId

    override fun getSoundSet(soundManager: SoundManager): WeightedSoundSet? = null

    override fun getSound(): Sound = sound

    override fun getCategory(): SoundCategory = engineCategory

    override fun isRepeatable(): Boolean = false

    override fun isRelative(): Boolean = isRelative

    override fun getRepeatDelay(): Int = 0

    override fun getVolume(): Float = volume * sound.volume.get(RANDOM)

    override fun getPitch(): Float = pitch * sound.pitch.get(RANDOM)

    override fun getX(): Double = x.toDouble()

    override fun getY(): Double = y.toDouble()

    override fun getZ(): Double = z.toDouble()

    override fun getAttenuationType(): AttenuationType = when(attenuate) {
        true -> AttenuationType.LINEAR
        false -> AttenuationType.NONE
    }

    companion object {
        private val RANDOM = Random.create()
    }
}
