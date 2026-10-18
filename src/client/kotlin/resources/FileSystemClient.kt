package org.lain.engine.client.resources

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.io.FileInputStream
import java.io.Reader
import java.util.concurrent.atomic.AtomicReference

@JvmInline
value class SourceFile(val file: File) {
    inline fun <reified T : Any> yaml(): T {
        return Yaml.default.decodeFromStream<T>(file.inputStream())
    }

    fun openInputStream(): FileInputStream = file.inputStream()

    fun <R> read(use: (Reader) -> R) = openInputStream().use {
        it.reader().use(use)
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

data class ResourceContext(
    val chatBarConfiguration: ChatBarConfiguration?,
    val formatConfiguration: ChatFormatSettings,
)

private fun bakeResourceContext(serverId: ServerId?): ResourceContext {
    return ResourceContext(
        CHAT_BAR_CONFIG.fetch(serverId)?.yaml(),
        FORMAT_CONFIG.fetch(serverId).getOrThrow().yaml(),
    )
}

private val CHAT_BAR_CONFIG = OverridableResource(FileSystem.CHAT_BAR_CONFIG_NAME)
private val FORMAT_CONFIG = OverridableResource(FileSystem.FORMAT_CONFIG_NAME)

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
