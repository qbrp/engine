package org.lain.engine.client.mc

import net.minecraft.client.sound.AbstractSoundInstance
import net.minecraft.client.sound.TickableSoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.random.Random
import org.lain.engine.player.Hearing
import org.lain.engine.player.TINNITUS_HEAR_THRESHOLD
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