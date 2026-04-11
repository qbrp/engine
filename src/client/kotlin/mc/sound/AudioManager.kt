package org.lain.engine.client.mc.sound

import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.*
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.floatprovider.ConstantFloatProvider
import org.lain.cyberia.ecs.require
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.ClientMixinAccess
import org.lain.engine.client.mixin.SoundManagerAccessor
import org.lain.engine.client.mixin.SoundSystemAccessor
import org.lain.engine.client.util.AudioSource
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.player.Hearing
import org.lain.engine.util.EngineId
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import org.lain.engine.world.SoundPlay
import kotlin.random.Random

class MinecraftAudioManager(
    private val client: MinecraftClient
) : EngineAudioManager {
    private var tinnitus: TinnitusSoundInstance? = null
    private val soundManager get() = client.soundManager
    private val soundSystem get() = (soundManager as SoundManagerAccessor).`engine$getSoundSystem`()
    private val soundSetCache = SoundSetCache()
    private val audioSources: MutableMap<String, AudioSourcePlayback> = HashMap()
    private val soundCache: MutableMap<String, Sound> = HashMap()

    private class AudioSourcePlayback(val source: AudioSource, val instance: AudioSourceSoundInstance) {
        fun update() {
            instance.isRelative = source.isRelative
            instance.x = source.x
            instance.y = source.y
            instance.z = source.z
            instance.attenuate = source.attenuate
            instance.pitch = source.pitch
            instance.volume = source.volume
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
        tinnitus = null
    }

    override fun playSound(player: SoundPlay) {
        val event = player.sound
        val soundSet = soundSetCache.get(event)
        soundManager.play(
            ServerSoundInstance(
                EngineId(event.id.value),
                player.volume,
                player.pitch,
                soundSet,
                player.category.toMinecraft(),
                ImmutableVec3(player.pos)
            )
        )
    }

    override fun containsAudioSource(slot: String): Boolean {
        return audioSources.containsKey(slot)
    }

    override fun addAudioSource(audioSource: AudioSource, slot: String) {
        if (audioSources.contains(slot)) error("Duplicate audio source $slot")
        val slotId = EngineId(slot)
        audioSources[slot] = AudioSourcePlayback(
            audioSource,
            AudioSourceSoundInstance(
                slotId,
                getSound(audioSource.sound.value),
                audioSource.category.toMinecraft()
            )
        ).also { it.update() }
    }

    private fun tickAudioSources() {
        audioSources.forEach { (slot, playback) ->
            playback.update()
            if (!soundManager.isPlaying(playback.instance)) {
                audioSources.remove(slot)
            }
        }
    }

    private fun getSound(id: String, stream: Boolean = false, preload: Boolean = false): Sound {
        return soundCache.computeIfAbsent(id) {
            Sound(
                EngineId(id),
                { 1f },
                { 1f },
                1,
                Sound.RegistrationType.FILE,
                stream,
                preload,
                16
            )
        }
    }

    private fun playMaster(sound: net.minecraft.sound.SoundEvent, pitch: Float, volume: Float = 0.25f) {
        soundManager.play(PositionedSoundInstance.master(sound, pitch, volume))
    }

    private fun playMaster(sound: RegistryEntry<net.minecraft.sound.SoundEvent>, pitch: Float) {
        soundManager.play(PositionedSoundInstance.master(sound, pitch))
    }

    fun tick(gameSession: GameSession) {
        updatePlayerTinnitus(gameSession)
        tickAudioSources()
    }

    private fun updatePlayerTinnitus(gameSession: GameSession) {
        val hearing = gameSession.mainPlayer.require<Hearing>()
        if (hearing.loss > 0.01f) {
            if (tinnitus == null) {
                val soundInstance = TinnitusSoundInstance(hearing)
                soundManager.play(soundInstance)
                tinnitus = soundInstance
            }
            soundSystem.updateVolume()
        } else if (hearing.loss < 0.01f) {
            tinnitus = null
        }
    }

    /** @see ClientMixinAccess.editVolume **/
    private fun SoundSystem.updateVolume() {
        (this as SoundSystemAccessor).`engine$getSources`().forEach { (source, manager) ->
            manager.run { it.setVolume(`engine$getAdjustedVolume`(source)) }
        }
    }

    class SoundSetCache {
        private val sets: MutableMap<SoundEventId, WeightedSoundSet> = mutableMapOf()

        fun invalidate() {
            sets.clear()
        }

        fun get(event: SoundEvent): WeightedSoundSet {
            return sets.computeIfAbsent(event.id) {
                val set = WeightedSoundSet(EngineId(event.id.value), null)
                event.sources
                    .map {
                        Sound(
                            EngineId(it.id.value),
                            ConstantFloatProvider.create(it.volume),
                            { random -> it.pitch + (it.pitchRandom * random.nextFloat() * 2 - it.pitchRandom) },
                            it.weight,
                            Sound.RegistrationType.FILE,
                            false,
                            false,
                            it.distance
                        ) as SoundContainer<Sound>
                    }
                    .forEach { set.add(it) }
                set
            }
        }
    }

    companion object {
        val PIG_SCREAM = registerSoundEvent("pig-scream")
        val KICK = registerSoundEvent("kick")
    }
}

fun registerSoundEvent(id: String): net.minecraft.sound.SoundEvent {
    return Registry.register(Registries.SOUND_EVENT, EngineId(id), net.minecraft.sound.SoundEvent.of(EngineId(id)))
}