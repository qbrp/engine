package org.lain.engine.util.file

import org.lain.engine.Constants
import org.lain.engine.server.ServerId
import java.io.File
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences
import kotlin.io.path.copyTo

object FileSystem {
    const val ROOT_PATH = "engine"

    const val COMPILATION_ENTRYPOINT_NAME = "install.lua"
    const val CONTENTS_PATH = "contents"
    const val MODULES_PATH = "modules"
    const val MODULE_SET_NAME = "modules.yaml"
    const val SCRIPTS_PATH = "scripts"
    const val LEGACY_ITEMS_PATH = "items"
    const val SERVER_CONFIG_NAME = "server-config.yml"
    const val DEBUG_PATH = "debug"
    const val COMPILATION_MANIFEST_NAME = "compilation-manifest.json"
    const val STORAGE_PATH = "storage"
    const val BOOK_BACKUPS_PATH = "books"
    const val SKINS_PATH = "skins"
    const val ACCOUNT_CACHE_NAME = "account.json"
    const val ASSETS_PATH = "assets"
    const val WALLPAPERS_PATH = "wallpapers"
    const val WEB_PATH = "web"
    const val EXTENSIONS_PATH = "extensions"
    const val GRAPHENE_JCEF_PATH = "graphene-jcef"
    const val CHAT_BAR_CONFIG_NAME = "chat-bar.yml"
    const val FORMAT_CONFIG_NAME = "format.yml"

    const val DEFAULT_RESOURCES_PATH = "defaults"

    val root: File = ensureDirectory(File(ROOT_PATH))
    val modules = ensureDirectory(root.resolve(MODULES_PATH))
    val moduleSet = modules.resolve(MODULE_SET_NAME)
        .apply {
            if (!exists()) {
                val resource = builtinResource("modules/$MODULE_SET_NAME")
                resource?.let { writeText(it.readText()) }
            }
        }

    val scripts: File = ensureDirectory(root.resolve(SCRIPTS_PATH))
    val compilationEntrypoint: File = scripts.resolve(COMPILATION_ENTRYPOINT_NAME)
    val contents: File = ensureDirectory(root.resolve(CONTENTS_PATH))
    val builtinScripts: File = scripts

    val serverConfig: File = root.resolve(SERVER_CONFIG_NAME)

    val storage: File = ensureDirectory(root.resolve(STORAGE_PATH))

    val bookBackups: File = ensureDirectory(storage.resolve(BOOK_BACKUPS_PATH))

    val skins: File = ensureDirectory(root.resolve(SKINS_PATH))
    val wallpapers: File = ensureDirectory(root.resolve(WALLPAPERS_PATH))

    val accountCache: File = root.resolve(ACCOUNT_CACHE_NAME)
    val web: File = root.resolve(WEB_PATH)

    val extensions: File = root.resolve(EXTENSIONS_PATH)
    val grapheneJcef: File = File(GRAPHENE_JCEF_PATH)

    val preferences: Preferences = Preferences.userRoot().node(ROOT_PATH)

    fun serverDirectory(serverId: ServerId): File = root.resolve(serverId.value)

    fun defaultResource(path: String): File = root.resolve(path)

    fun serverResource(serverId: ServerId, path: String): File =
        serverDirectory(serverId).resolve(path)

    fun compilationEntrypoint(directory: File): File =
        directory.resolve(SCRIPTS_PATH).resolve(COMPILATION_ENTRYPOINT_NAME)

    fun compilationManifestFile(scriptsDirectory: File): File {
        val engineDirectory = scriptsDirectory.parentFile ?: root
        return engineDirectory.resolve(DEBUG_PATH).resolve(COMPILATION_MANIFEST_NAME)
    }

    fun ensureFile(file: File): File {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        return file
    }

    fun ensureDirectory(directory: File): File {
        directory.mkdirs()
        return directory
    }

    fun builtinResource(path: String): URL? {
        val classLoader = Thread.currentThread().contextClassLoader
        return classLoader.getResource("$DEFAULT_RESOURCES_PATH/$path")
    }

    fun builtinResources(path: String): List<Path> {
        val uri = requireNotNull(builtinResource(path)) {
            "Встроенный ресурс $path не найден"
        }.toURI()
        val root = when (uri.scheme) {
            "jar" -> {
                val fileSystem = try {
                    FileSystems.getFileSystem(uri)
                } catch (_: Exception) {
                    FileSystems.newFileSystem(uri, emptyMap<String, Any>())
                }
                fileSystem.getPath("$DEFAULT_RESOURCES_PATH/$path")
            }

            else -> Paths.get(uri)
        }

        return Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList()
        }
    }

    fun loadStandardLuaLibrary() {
        if (!Constants.LOAD_LUA_LIBRARIES) return
        if (Constants.DEVELOPER_TEST_ENVIRONMENT) return

        builtinResources(SCRIPTS_PATH).forEach { source ->
            println("Loading script $source")
            val relative = source.toString()
                .replace("\\", "/")
                .substringAfter("$SCRIPTS_PATH/")
            val target = builtinScripts.resolve(relative).toPath()

            Files.createDirectories(target.parent)
            source.copyTo(target, overwrite = true)
        }
    }

    fun migrateLegacyStructure() {
        val items = root.resolve(LEGACY_ITEMS_PATH)
        if (items.exists()) {
            items.renameTo(contents)
            CONFIG_LOGGER.warn("Файл старого формата engine/items переименован в engine/contents")
        }
    }
}
