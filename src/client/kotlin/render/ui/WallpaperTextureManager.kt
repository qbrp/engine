package org.lain.engine.client.render.ui

import com.mojang.blaze3d.platform.NativeImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.engineId
import org.lain.engine.util.file.FileSystem
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Locale
import kotlin.random.Random

class WallpaperTextureManager(
    private val client: Minecraft,
    private val directory: File = FileSystem.wallpapers,
    private val random: Random = Random.Default,
) : AutoCloseable {
    data class Wallpaper(
        val id: ResourceLocation,
        val width: Int,
        val height: Int,
        val source: File,
    )

    private data class LoadedWallpaper(
        val wallpaper: Wallpaper,
        val texture: DynamicTexture,
    )

    private var scope = newScope()
    private var generation = 0
    private var textureSequence = 0L
    private var sources: List<File> = emptyList()
    private var currentLoaded: LoadedWallpaper? = null
    private var nextLoaded: LoadedWallpaper? = null
    private var preloadSource: File? = null
    private var initialLoadJob: Job? = null
    private var preloadJob: Job? = null
    private val pendingRelease = mutableListOf<LoadedWallpaper>()

    val current: Wallpaper?
        get() = currentLoaded?.wallpaper

    val next: Wallpaper?
        get() = nextLoaded?.wallpaper

    fun reload() {
        generation += 1
        scope.cancel()
        scope = newScope()
        initialLoadJob = null
        preloadJob = null
        preloadSource = null
        currentLoaded?.let(pendingRelease::add)
        nextLoaded?.let(pendingRelease::add)
        currentLoaded = null
        nextLoaded = null
        sources = directory
            .walkTopDown()
            .filter(File::isFile)
            .filter { it.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS }
            .sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
            .toList()

        if (sources.isEmpty()) {
            LOGGER.warn("В директории {} не найдено обоев", directory.absolutePath)
            return
        }

        val expectedGeneration = generation
        initialLoadJob = launchLoad(sources.shuffled(random), expectedGeneration) { loaded ->
            currentLoaded = loaded
            preloadNext()
        }
    }

    fun beginFrame() {
        releasePending()
        if (currentLoaded != null && nextLoaded == null && preloadSource == null) {
            preloadNext()
        }
    }

    fun advance(): Boolean {
        val incoming = nextLoaded ?: return false
        currentLoaded?.let(pendingRelease::add)
        currentLoaded = incoming
        nextLoaded = null
        preloadSource = null
        preloadJob = null
        return true
    }

    override fun close() {
        generation += 1
        scope.cancel()
        initialLoadJob = null
        preloadJob = null
        preloadSource = null
        currentLoaded?.let(pendingRelease::add)
        nextLoaded?.let(pendingRelease::add)
        currentLoaded = null
        nextLoaded = null
        releasePending()
    }

    private fun preloadNext() {
        val currentSource = currentLoaded?.wallpaper?.source ?: return
        if (sources.size < 2 || nextLoaded != null || preloadSource == currentSource) return

        val candidates = sources.filterNot { it == currentSource }.shuffled(random)
        preloadSource = currentSource
        val expectedGeneration = generation
        preloadJob = launchLoad(candidates, expectedGeneration) { loaded ->
            if (currentLoaded?.wallpaper?.source == currentSource) {
                nextLoaded = loaded
            } else {
                pendingRelease += loaded
            }
        }
    }

    private fun launchLoad(
        candidates: List<File>,
        expectedGeneration: Int,
        accept: (LoadedWallpaper) -> Unit,
    ): Job = scope.launch {
        val decoded = decodeFirst(candidates) ?: return@launch
        val source = decoded.first
        val image = decoded.second
        val width = image.width
        val height = image.height
        var imageOwnedByTexture = false

        try {
            withContext(MinecraftClientDispatcher) {
                if (generation != expectedGeneration) return@withContext

                val id = engineId("wallpapers/runtime_${textureSequence++}")
                val texture = DynamicTexture(image)
                imageOwnedByTexture = true
                try {
                    client.textureManager.register(id, texture)
                    if (generation == expectedGeneration) {
                        accept(LoadedWallpaper(Wallpaper(id, width, height, source), texture))
                    } else {
                        client.textureManager.release(id)
                    }
                } catch (exception: Throwable) {
                    texture.close()
                    throw exception
                }
            }
        } finally {
            if (!imageOwnedByTexture) image.close()
        }
    }

    private fun decodeFirst(candidates: List<File>): Pair<File, NativeImage>? {
        candidates.forEach { source ->
            try {
                return source to source.inputStream().use(NativeImage::read)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                LOGGER.error("Не удалось загрузить обои {}", source.absolutePath, exception)
            }
        }
        return null
    }

    private fun releasePending() {
        pendingRelease.forEach { client.textureManager.release(it.wallpaper.id) }
        pendingRelease.clear()
    }

    private fun newScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Engine Wallpaper Texture Manager")
        private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg")
    }
}
