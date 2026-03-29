package org.lain.engine.client.mc

import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.*
import net.minecraft.client.sound.SoundInstance.AttenuationType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.util.math.floatprovider.ConstantFloatProvider
import org.lain.engine.client.GameSession
import org.lain.engine.client.mixin.SoundManagerAccessor
import org.lain.engine.client.mixin.SoundSystemAccessor
import org.lain.engine.client.resources.Assets
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.player.Hearing
import org.lain.engine.util.EngineId
import org.lain.engine.util.component.require
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.world.EngineSoundCategory
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import org.lain.engine.world.SoundPlay
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
    private var tinnitus: TinnitusSoundInstance? = null
    private val soundManager get() = client.soundManager
    private val soundSystem get() = (soundManager as SoundManagerAccessor).`engine$getSoundSystem`()
    private val soundSetCache = SoundSetCache()

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

    fun updatePlayerTinnitus(gameSession: GameSession) {
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
        val tinnitus = tinnitus
        (this as SoundSystemAccessor).`engine$getSources`().forEach { (source, manager) ->
            manager.run { it.setVolume(`engine$getAdjustedVolume`(source)) }
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