package org.lain.engine.client.account

import com.mojang.blaze3d.platform.NativeImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.ClientAsset
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.resources.SKINS_DIR
import org.lain.engine.client.util.EngineOptions
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.client.util.launchClientContext
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.engineId
import org.lain.engine.mc.server.HttpStatusException
import org.lain.engine.player.character.Look
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SkinTextureManager(
    private val httpClient: HttpClient
) : AutoCloseable {
    private val skinRequestTimeout = java.time.Duration.ofSeconds(10)
    private val maxSkinBytes = 1024 * 1024
    private val logger = LoggerFactory.getLogger("Engine Skin Texture Manager")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loaded = ConcurrentHashMap<String, LoadedSkin>()
    private val loading = ConcurrentHashMap.newKeySet<String>()

    private var skinDownloadRetryDelay = AtomicInteger(5)

    fun onOptions(options: EngineOptions) {
        skinDownloadRetryDelay.set(options.skinDownloadRetryDelay)
    }

    fun preload(look: Look) {
        scope.launch { getTexture(look) }
    }

    fun getTexture(look: Look): ClientAsset.Texture {
        loaded[look.id]?.let { return it.asset }

        if (loading.add(look.id)) {
            scope.launch { loadSkin(look) }
        }

        return DefaultPlayerSkin.getDefaultSkin().body
    }

    override fun close() {
        loaded.values.forEach { loadedSkin ->
            MinecraftClient.textureManager.release(loadedSkin.asset.texturePath())
            loadedSkin.texture.close()
        }
        loaded.clear()
        loading.clear()
    }

    private suspend fun loadSkin(look: Look) {
        try {
            val file = cachePath(look)
            if (Files.exists(file)) {
                runCatching { validateSkinBytes(Files.readAllBytes(file)) }
                    .onFailure {
                        logger.warn("Dropping invalid cached character skin ${look.id}: ${it.message}")
                        Files.deleteIfExists(file)
                    }
            }

            if (!Files.exists(file)) {
                val bytes = loadHttp(look.skin.url)
                validateSkinBytes(bytes)
                writeAtomically(file, bytes)
            }

            register(look.id, file, look.skin.url)
        } catch (e: Throwable) {
            logger.error("Не удалось загрузить скин образа ${look.id} с ${look.skin.url}", e)
            delay(skinDownloadRetryDelay.toLong() * 1000L) // чтобы не делать слишком частые повторы
        } finally {
            loading.remove(look.id)
        }
    }

    private suspend fun loadHttp(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(skinRequestTimeout)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        if (response.statusCode() !in 200..299) {
            throw HttpStatusException(response.statusCode(), request.uri(), response.body().decodeToString())
        }

        val contentType = response.headers().firstValue("Content-Type").orElse(null)
        if (contentType != null && !contentType.substringBefore(";").trim().startsWith("image/")) {
            throw IllegalArgumentException("Ответ сервера не является изображением: $contentType")
        }

        val contentLength = response.headers().firstValueAsLong("Content-Length")
        if (contentLength.isPresent && contentLength.asLong > maxSkinBytes) {
            throw IllegalArgumentException("Ответ сервера слишком большой: ${contentLength.asLong} байт")
        }

        response.body().also { bytes ->
            if (bytes.size > maxSkinBytes) {
                throw IllegalArgumentException("Ответ сервера слишком большой: ${bytes.size} bytes")
            }
        }
    }

    private suspend fun register(lookId: String, file: Path, url: String) = withContext(Dispatchers.IO) {
        val bytes = Files.readAllBytes(file)
        validateSkinBytes(bytes)
        val image = NativeImage.read(bytes)
        val textureId = engineId("character_skins/$lookId")
        val asset = ClientAsset.DownloadedTexture(textureId, url)

        withContext(MinecraftClientDispatcher) {
            if (loaded.containsKey(lookId)) {
                image.close()
                return@withContext
            }

            val texture = DynamicTexture({ "Engine character skin $lookId" }, image)
            MinecraftClient.textureManager.register(textureId, texture)
            texture.upload()
            loaded[lookId] = LoadedSkin(asset, texture)
        }
    }

    private fun writeAtomically(file: Path, bytes: ByteArray) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.write(temporary, bytes)
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun validateSkinBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("Изображение скина пустое")
        }
        if (bytes.size > maxSkinBytes) {
            throw IllegalArgumentException("Изображение скина слишком большое: ${bytes.size} байт")
        }

        val image = runCatching { NativeImage.read(bytes) }
            .getOrElse { throw IllegalArgumentException("Невозможно декодировать изображение скина", it) }

        image.use {
            val validSize = (it.width == 64 && it.height == 64) || (it.width == 64 && it.height == 32)
            if (!validSize) {
                throw IllegalArgumentException("Размер скина неправильный: ${it.width}x${it.height}")
            }
        }
    }

    private fun cachePath(look: Look): Path {
        val cacheKey = cacheKey(look)
        return SKINS_DIR
            .resolve(cacheKey.substring(0, 2))
            .resolve(cacheKey.substring(2, 4))
            .resolve("$cacheKey.png")
            .toPath()
    }

    private fun cacheKey(look: Look): String = sha256("${look.id}:${look.skin.url}")

    private fun sha256(value: String): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class LoadedSkin(
        val asset: ClientAsset.Texture,
        val texture: DynamicTexture,
    )
}
