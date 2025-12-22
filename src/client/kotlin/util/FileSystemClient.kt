package org.lain.engine.client.util

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.ChatBarConfiguration
import org.lain.engine.client.chat.ChatFormatSettings
import org.lain.engine.client.mc.ClientEngineItemGroups
import org.lain.engine.server.ServerId
import org.lain.engine.util.ENGINE_DIR
import org.lain.engine.util.replaceLazy
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

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
    val path: String
) {
    fun fetch(server: ServerId? = null): SourceFile? {
        val default = SourceFile.nullable(ENGINE_DIR.resolve(path))

        val server = server?.let {
            val file = getServerFile(it).resolve(path)
            SourceFile.nullable(file)
        }

        return server ?: default
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
            val relativePath = relative.path
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

val File.identifier get() = this.normalize()
    .toString()
    .replace('\\', '/')
    .let {
        if (!extension.isEmpty()) it.dropLast(extension.count() + 1) else it
    }

data class ResourceContext(
    val assets: Assets,
    val chatBarConfiguration: ChatBarConfiguration?,
    val formatConfiguration: ChatFormatSettings,
    val itemGroups: ClientEngineItemGroups,
    val autogenerationItemAssets: AutoGenerationList = assets.autogenerationItemAssets
)

@Serializable
data class AutoGenerationList(val textures: List<String> = listOf())

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
private val ASSETS = OverridableResource("assets")

class ResourceManager(
    private val client: EngineClient
) {
    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO)
    private val _context = AtomicReference(bakeResourceContext(client.gameSession))
    val context: ResourceContext
        get() = _context.get()

    fun reload(gameSession: GameSession) = ioCoroutineScope.async {
        _context.set(bakeResourceContext(gameSession))
    }
}