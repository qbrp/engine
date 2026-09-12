package org.lain.engine.client.resources

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.client.EngineClient
import org.lain.engine.client.chat.ChatBarConfiguration
import org.lain.engine.client.chat.ChatFormatSettings
import org.lain.engine.client.render.WARNING
import org.lain.engine.client.render.LittleNotification
import org.lain.engine.server.ServerId
import org.lain.engine.util.WARNING_COLOR
import org.lain.engine.util.file.FileSystem
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@JvmInline
value class SourceFile(val file: File) {
    inline fun <reified T : Any> yaml(): T {
        return Yaml.default.decodeFromStream<T>(file.inputStream())
    }

    fun openInputStream() = file.inputStream()

    fun resolve(child: String) = nullable(file.resolve(child))

    companion object {
        fun nullable(file: File): SourceFile? {
            return if (!file.exists()) {
                null
            } else {
                SourceFile(file)
            }
        }
    }
}

fun File.toSourceFile() = SourceFile(this)

fun SourceFile?.getOrThrow() = this ?: error("Файл не найден")

data class OverridableResource(
    val path: String,
    val isFile: Boolean = false
) {
    fun fetch(server: ServerId? = null): SourceFile? {
        val defaultPath = FileSystem.defaultResource(path)
        val default = SourceFile.nullable(defaultPath)

        val server = server?.let {
            val file = FileSystem.serverResource(it, path)
            SourceFile.nullable(file)
        }

        return server ?: default ?: run {
            if (isFile) {
                defaultPath.mkdirs()
            } else {
                val builtin = FileSystem.builtinResource(path) ?: return null
                defaultPath.writeText(builtin.readText())
            }
            defaultPath.toSourceFile()
        }
    }
}

typealias AssetPacker = () -> Asset

class Assets(val source: SourceFile) {
    val directory = source.file
    val spriteAtlases =
        source.resolve("atlases.yml")?.yaml<SpriteAtlasRules>() ?: SpriteAtlasRules()
    val autogenerationItemAssets =
        source.resolve("autogenerate.yml")?.yaml<AutoGenerationList>() ?: AutoGenerationList()

    fun getAsset(relative: String): Asset? {
        val relative = File(relative)
        val file = directory.resolve(relative)
        return Asset(relative, SourceFile.nullable(file) ?: return null)
    }

    fun browseAssets(block: (String, File, AssetPacker) -> Unit) {
        directory.walk().forEach { file ->
            if (!file.isFile) return@forEach
            val relative = file.relativeTo(directory)
            val relativePath = relative.path.normalizeSlashes()
            val packer: AssetPacker = { Asset(relative, SourceFile(file)) }
            block(relativePath, file, packer)
        }
    }
}

data class Asset(
    val relative: File,
    val source: SourceFile
) {
    val relativeString: String = relative.path
    val relativeParent: File
        get() = File(relative.parent)

}

data class ResourceContext(
    val assets: Assets,
    val contents: SourceFile,
    val web: SourceFile,
    val chatBarConfiguration: ChatBarConfiguration?,
    val formatConfiguration: ChatFormatSettings,
    val autogenerationItemAssets: AutoGenerationList = assets.autogenerationItemAssets
)

@Serializable
data class AutoGenerationList(
    val autogen: List<Entry> = emptyList()
) {
    @Serializable
    data class Entry(
        @SerialName("texture") val assetPath: String,
        val type: String
    )
}

private fun bakeResourceContext(serverId: ServerId?): ResourceContext {
    val assetsSource = ASSETS.fetch(serverId).getOrThrow()
    val assets = Assets(assetsSource)


    return ResourceContext(
        assets,
        CONTENTS.fetch(serverId).getOrThrow(),
        WEB.fetch(serverId).getOrThrow(),
        CHAT_BAR_CONFIG.fetch(serverId)?.yaml(),
        FORMAT_CONFIG.fetch(serverId).getOrThrow().yaml(),
    )
}

private val CHAT_BAR_CONFIG = OverridableResource(FileSystem.CHAT_BAR_CONFIG_NAME)
private val FORMAT_CONFIG = OverridableResource(FileSystem.FORMAT_CONFIG_NAME)
private val ASSETS = OverridableResource(FileSystem.ASSETS_PATH, true)
private val CONTENTS = OverridableResource(FileSystem.CONTENTS_PATH, true)
private val WEB = OverridableResource(FileSystem.WEB_PATH, true)

class ResourceManager(
    private val client: EngineClient
) {
    private val logger = LoggerFactory.getLogger("Engine Resources")
    private val _context = AtomicReference(bakeResourceContext(null))
    val context: ResourceContext
        get() = _context.get()

    suspend fun reload(serverId: ServerId) = withContext(Dispatchers.IO) {
        try {
            _context.set(bakeResourceContext(serverId))
        } catch (e: Throwable) {
            client.execute {
                client.showNotification(
                    LittleNotification(
                        "Ошибка загрузки ресурсов",
                        "Проверьте консоль для подробного отчёта. Сообщение: ${e.message ?: "Неизвестная ошибка"}",
                        sprite = WARNING,
                        color = WARNING_COLOR,
                        lifeTime = 200
                    )
                )
            }
            logger.error("Ошибка загрузки ресурсов", e)
        }
    }
}
