package org.lain.engine.client.mc.sound

import com.mojang.blaze3d.audio.SoundBuffer
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import org.lain.engine.client.EngineClient
import org.lain.engine.script.EngineId
import org.lain.engine.world.EngineSoundCategory
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

fun EngineSoundCategory.toMinecraft() = when (this) {
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
    client: EngineClient,
    loadedSounds: MutableMap<ResourceLocation, CompletableFuture<SoundBuffer>>,
    id: ResourceLocation
): CompletableFuture<SoundBuffer> {
    val compilation = client.assetCompilation ?: error("Компиляция ассетов не начата")
    return loadedSounds.computeIfAbsent(id) {
        compilation.scope.async {
            try {
                val assets = compilation.indexedAssets.await()
                val soundAsset = assets.sounds[EngineId(id.path)] ?: error("Ассет звука $id не найден")
                soundAsset.source.openInputStream().use { inputStream ->
                    JOrbisAudioStream(inputStream).use { nonRepeatingAudioStream ->
                        val byteBuffer = nonRepeatingAudioStream.readAll()
                        SoundBuffer(byteBuffer, nonRepeatingAudioStream.format)
                    }
                }
            } catch (iOException: IOException) {
                throw CompletionException(iOException)
            }
        }.asCompletableFuture()
    }
}
