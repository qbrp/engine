package org.lain.engine.client.mc.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.valueproviders.ConstantFloat
import org.lain.engine.client.GameSession
import org.lain.engine.client.mixin.SoundManagerAccessor
import org.lain.engine.client.util.AudioSource
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.mc.engineId
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import org.lain.engine.world.SoundPlay
import kotlin.random.Random

class MinecraftAudioManager(
    private val client: Minecraft
) : EngineAudioManager {
    private val soundManager get() = client.soundManager
    private val soundSystem get() = (soundManager as SoundManagerAccessor).`engine$getSoundSystem`()
    private val soundSetCache = SoundSetCache()
    private val audioSources: MutableMap<String, AudioSourcePlayback> = HashMap()
    private val soundCache: MutableMap<SoundKey, Sound> = HashMap()

    data class SoundKey(val radius: Int, val id: String)

    private class AudioSourcePlayback(val source: AudioSource, val instance: AudioSourceSoundInstance) {
        fun update() {
            instance._isRelative = source.isRelative
            instance._x = source.x
            instance._y = source.y
            instance._z = source.z
            instance.attenuate = source.attenuate
            instance._pitch = source.pitch
            instance._volume = source.volume
        }
    }

    override fun playUiNotificationSound() {
        playMaster(SoundEvents.UI_BUTTON_CLICK, 1f - Random.nextFloat() / 7f)
        if (Random.nextFloat() > 0.98f) {
            playPigScreamSound()
        }
    }

    override fun playPigScreamSound() {
        playMaster(PIG_SCREAM, 1f - Random.nextFloat() / 8f, 2f)
    }

    override fun playKickSound() {
        playMaster(KICK, 1f - Random.nextFloat() / 8f, 2f)
    }

    override fun invalidateCache() {
        soundSetCache.invalidate()
    }

    override fun playSound(player: SoundPlay) {
        val event = player.sound
        val soundSet = soundSetCache.get(event)
        soundManager.play(
            ServerSoundInstance(
                engineId(event.id.value),
                player.volume,
                player.pitch,
                soundSet,
                player.category.toMinecraft(),
                ImmutableEVec3(player.pos)
            )
        )
    }

    override fun containsAudioSource(slot: String): Boolean {
        return audioSources.containsKey(slot)
    }

    override fun addAudioSource(audioSource: AudioSource, slot: String) {
        if (audioSources.contains(slot)) error("Duplicate audio source $slot")
        val slotId = engineId(slot)
        val playback = AudioSourcePlayback(
            audioSource,
            AudioSourceSoundInstance(
                slotId,
                getSound(audioSource.sound.value, attenuation = audioSource.radius),
                audioSource.category.toMinecraft()
            )
        )
        playback.update()
        audioSources[slot] = playback
        audioSource.slot = slot
        soundManager.play(playback.instance)
        audioSource.isEnded = false
    }

    override fun stopAudioSource(audioSource: AudioSource) {
        val slot = audioSource.slot ?: return
        audioSources[slot]?.let { soundManager.stop(it.instance) }
        audioSource.isEnded = true
        audioSource.slot = null
    }

    private fun tickAudioSources() {
        val remove = mutableListOf<String>()
        audioSources.forEach { (slot, playback) ->
            playback.update()
            val source = playback.source
            if (!soundManager.isActive(playback.instance)) {
                source.isEnded = true
                remove += slot
            } else {
                source.isEnded = false
            }
        }
        remove.forEach { audioSources.remove(it) }
    }

    private fun getSound(
        id: String,
        stream: Boolean = false,
        preload: Boolean = false,
        attenuation: Int,
    ): Sound {
        return soundCache.computeIfAbsent(SoundKey(attenuation, id)) {
            Sound(
                engineId(id),
                { 1f },
                { 1f },
                1,
                Sound.Type.FILE,
                stream,
                preload,
                attenuation
            )
        }
    }

    private fun playMaster(sound: net.minecraft.sounds.SoundEvent, pitch: Float, volume: Float = 0.25f) {
        soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
    }

    private fun playMaster(sound: Holder.Reference<net.minecraft.sounds.SoundEvent>, pitch: Float) {
        soundManager.play(SimpleSoundInstance.forUI(sound, pitch))
    }

    fun tick(gameSession: GameSession) {
        tickAudioSources()
    }

    class SoundSetCache {
        private val sets: MutableMap<SoundEventId, WeighedSoundEvents> = mutableMapOf()

        fun invalidate() {
            sets.clear()
        }

        fun get(event: SoundEvent): WeighedSoundEvents {
            return sets.computeIfAbsent(event.id) {
                val set = WeighedSoundEvents(engineId(event.id.value), null)
                event.sources
                    .map {
                        Sound(
                            engineId(it.id.value),
                            ConstantFloat.of(it.volume),
                            { random -> it.pitch + (it.pitchRandom * random.nextFloat() * 2 - it.pitchRandom) },
                            it.weight,
                            Sound.Type.FILE,
                            false,
                            false,
                            it.distance
                        )
                    }
                    .forEach { set.addSound(it) }
                set
            }
        }
    }

    companion object {
        val PIG_SCREAM = registerSoundEvent("pig-scream")
        val KICK = registerSoundEvent("kick")
    }
}

fun registerSoundEvent(id: String): net.minecraft.sounds.SoundEvent {
    return Registry.register(
        BuiltInRegistries.SOUND_EVENT,
        engineId(id),
        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(engineId(id))
    )
}