package org.lain.engine.client.mc

import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.util.EngineId
import kotlin.random.Random

class MinecraftAudioManager(
    private val client: MinecraftClient
) : EngineAudioManager {
    private val soundManager get() = client.soundManager

    override fun playUiNotificationSound() {
        playMaster(SoundEvents.UI_BUTTON_CLICK, 1f - Random.nextFloat() / 7f)
        if (Random.nextFloat() > 0.99f) {
            playPigScreamSound()
        }
    }

    override fun playPigScreamSound() {
        playMaster(PIG_SCREAM, 1f - Random.nextFloat() / 8f, 2f)
    }

    private fun playMaster(sound: SoundEvent, pitch: Float, volume: Float = 0.25f) {
        soundManager.play(PositionedSoundInstance.master(sound, pitch, volume))
    }

    private fun playMaster(sound: RegistryEntry<SoundEvent>, pitch: Float) {
        soundManager.play(PositionedSoundInstance.master(sound, pitch))
    }

    companion object {
        val PIG_SCREAM = register("pig-scream")

        private fun register(id: String): SoundEvent {
            return Registry.register(Registries.SOUND_EVENT, EngineId(id), SoundEvent.of(EngineId(id)))
        }
    }
}