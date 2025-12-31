package org.lain.engine.client.resources

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.ChatBarConfiguration
import org.lain.engine.client.chat.ChatFormatSettings
import org.lain.engine.client.mc.ClientEngineItemGroups
import org.lain.engine.client.render.WARNING
import org.lain.engine.client.render.WARNING_COLOR
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.server.ServerId
import org.lain.engine.util.ENGINE_DIR
import org.lain.engine.util.getBuiltinResource
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.writeText

fun getServerFile(serverId: ServerId) = ENGINE_DIR
    .resolve(serverId.value)

@JvmInline
value class SourceFile(val file: File) {
    inline fun <reified T : Any> yaml(): T {
        return Yaml.default.decodeFromStream<T>(file.inputStream())
    }

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
        val defaultPath = ENGINE_DIR.resolve(path)
        val default = SourceFile.nullable(defaultPath)

        val server = server?.let {
            val file = getServerFile(it).resolve(path)
            SourceFile.nullable(file)
        }

        return server ?: default ?: run {
            if (isFile) {
                defaultPath.mkdirs()
            } else {
                val builtin = getBuiltinResource(path) ?: return null
                defaultPath.writeText(builtin.readText())
            }
            defaultPath.toSourceFile()
        }
    }
}

typealias AssetPacker = () -> Asset

class Assets(val source: SourceFile) {
    val directory = source.file
    val autogenerationItemAssets = source.resolve("autogenerate.yml")
        ?.yaml<AutoGenerationList>()
        ?: AutoGenerationList()

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
    val chatBarConfiguration: ChatBarConfiguration?,
    val formatConfiguration: ChatFormatSettings,
    val itemGroups: ClientEngineItemGroups,
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

private fun bakeResourceContext(gameSession: GameSession?): ResourceContext {
    val serverId = gameSession?.server
    val assetsSource = ASSETS.fetch(serverId).getOrThrow()

    return ResourceContext(
        Assets(assetsSource),
        CHAT_BAR_CONFIG.fetch(serverId)?.yaml(),
        FORMAT_CONFIG.fetch(serverId).getOrThrow().yaml(),
        ITEM_GROUPS.fetch(serverId)?.yaml() ?: ClientEngineItemGroups()
    )
}

private val CHAT_BAR_CONFIG = OverridableResource("chat-bar.yml")
private val FORMAT_CONFIG = OverridableResource("format.yml")
private val ITEM_GROUPS = OverridableResource("item-groups.yml")
private val ASSETS = OverridableResource("assets", true)

class ResourceManager(
    private val client: EngineClient
) {
    private val logger = LoggerFactory.getLogger("Engine Resources")
    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _context = AtomicReference(bakeResourceContext(client.gameSession))
    val context: ResourceContext
        get() = _context.get()

    fun reload(gameSession: GameSession) = ioCoroutineScope.async {
        try {
            _context.set(bakeResourceContext(gameSession))
        } catch (e: Throwable) {
            client.execute {
                client.applyLittleNotification(
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