package org.lain.engine.client.mc.sound

import com.mojang.blaze3d.audio.SoundBuffer
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Util
import org.lain.engine.client.resources.Assets
import org.lain.engine.world.EngineSoundCategory
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor

fun EngineSoundCategory.toMinecraft() = when(this) {
    EngineSoundCategory.MASTER -> SoundSource.MASTER
    EngineSoundCategory.WEATHER -> SoundSource.WEATHER
    EngineSoundCategory.BLOCKS -> SoundSource.BLOCKS
    EngineSoundCategory.HOSTILE -> SoundSource.HOSTILE
    EngineSoundCategory.NEUTRAL -> SoundSource.NEUTRAL
    EngineSoundCategory.PLAYERS -> SoundSource.PLAYERS
    EngineSoundCategory.AMBIENT -> SoundSource.AMBIENT
    EngineSoundCategory.VOICE -> SoundSource.VOICE
}

fun loadEngineStaticSound(
    assets: Assets,
    loadedSounds: MutableMap<Identifier, CompletableFuture<SoundBuffer>>,
    id: Identifier
): CompletableFuture<SoundBuffer> {
    return loadedSounds.computeIfAbsent(id) { id2 ->
        CompletableFuture.supplyAsync(
            {
                try {
                    assets.getAsset(id2.path)?.source?.openInputStream().use { inputStream ->
                        val staticSound: SoundBuffer?
                        JOrbisAudioStream(inputStream!!).use { nonRepeatingAudioStream ->
                            val byteBuffer = nonRepeatingAudioStream.readAll()
                            staticSound = SoundBuffer(byteBuffer, nonRepeatingAudioStream.format)
                        }
                        return@supplyAsync staticSound
                    }
                } catch (iOException: IOException) {
                    throw CompletionException(iOException)
                }
            },
            Util.nonCriticalIoPool() as Executor
        )
    }
}