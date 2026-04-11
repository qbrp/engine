package org.lain.engine.client.mc.sound

import net.minecraft.client.sound.OggAudioStream
import net.minecraft.client.sound.StaticSound
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import org.lain.engine.client.resources.Assets
import org.lain.engine.world.EngineSoundCategory
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor

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