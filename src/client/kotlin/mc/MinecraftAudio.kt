package org.lain.engine.client.mc

import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.*
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.sound.SoundInstance.AttenuationType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.util.math.floatprovider.ConstantFloatProvider
import org.lain.engine.client.resources.Assets
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.item.EngineSoundCategory
import org.lain.engine.item.SoundEvent
import org.lain.engine.item.SoundEventId
import org.lain.engine.item.SoundPlay
import org.lain.engine.util.EngineId
import org.lain.engine.util.math.ImmutableVec3
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import kotlin.random.Random

fun loadEngineStaticSound(
    assets: Assets,
    loadedSounds: MutableMap<Identifier, CompletableFuture<StaticSound>>,
    id: Identifier
): CompletableFuture<StaticSound> {
    return loadedSounds.computeIfAbsent(id) { id2 ->
        CompletableFuture.supplyAsync(
             {
                try {
                    assets.getAsset(id2.path)?.source?.openInputStream().use { inputStream ->
                        val staticSound: StaticSound?
                        OggAudioStream(inputStream).use { nonRepeatingAudioStream ->
                            val byteBuffer = nonRepeatingAudioStream.readAll()
                            staticSound = StaticSound(byteBuffer, nonRepeatingAudioStream.format)
                        }
                        return@supplyAsync staticSound
                    }
                } catch (iOException: IOException) {
                    throw CompletionException(iOException)
                }
            },
            Util.getDownloadWorkerExecutor() as Executor
        )
    }
}

fun EngineSoundCategory.toMinecraft() = when(this) {
    EngineSoundCategory.MASTER -> SoundCategory.MASTER
    EngineSoundCategory.WEATHER -> SoundCategory.WEATHER
    EngineSoundCategory.BLOCKS -> SoundCategory.BLOCKS
    EngineSoundCategory.HOSTILE -> SoundCategory.HOSTILE
    EngineSoundCategory.NEUTRAL -> SoundCategory.NEUTRAL
    EngineSoundCategory.PLAYERS -> SoundCategory.PLAYERS
    EngineSoundCategory.AMBIENT -> SoundCategory.AMBIENT
    EngineSoundCategory.VOICE -> SoundCategory.VOICE
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

data class EngineSoundInstance(
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

class MinecraftAudioManager(
    private val client: MinecraftClient
) : EngineAudioManager {
    private val soundManager get() = client.soundManager
    private val soundSetCache = SoundSetCache()

    override fun playUiNotificationSound() {
        playMaster(SoundEvents.UI_BUTTON_CLICK, 1f - Random.nextFloat() / 7f)
        if (Random.nextFloat() > 0.99f) {
            playPigScreamSound()
        }
    }

    override fun playPigScreamSound() {
        playMaster(PIG_SCREAM, 1f - Random.nextFloat() / 8f, 2f)
    }

    override fun invalidateCache() {
        soundSetCache.invalidate()
    }

    override fun playSound(player: SoundPlay) {
        val event = player.sound
        val soundSet = soundSetCache.get(event)
        soundManager.play(
            EngineSoundInstance(
                EngineId(event.id.value),
                player.volume,
                player.pitch,
                soundSet,
                player.category.toMinecraft(),
                ImmutableVec3(player.pos)
            )
        )
    }

    private fun playMaster(sound: net.minecraft.sound.SoundEvent, pitch: Float, volume: Float = 0.25f) {
        soundManager.play(PositionedSoundInstance.master(sound, pitch, volume))
    }

    private fun playMaster(sound: RegistryEntry<net.minecraft.sound.SoundEvent>, pitch: Float) {
        soundManager.play(PositionedSoundInstance.master(sound, pitch))
    }

    companion object {
        val PIG_SCREAM = register("pig-scream")

        private fun register(id: String): net.minecraft.sound.SoundEvent {
            return Registry.register(Registries.SOUND_EVENT, EngineId(id), net.minecraft.sound.SoundEvent.of(EngineId(id)))
        }
    }
}