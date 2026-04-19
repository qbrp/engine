package org.lain.engine.util.file

import org.lain.engine.SharedConstants
import org.lain.engine.script.contents
import org.lain.engine.script.scripts
import java.io.File
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences
import kotlin.io.path.copyTo

val ENGINE_DIR = File("engine")
    .also { it.mkdirs() }

val BUILTIN_SCRIPTS_DIR = ENGINE_DIR.scripts
    .also { it.mkdirs() }

val ENGINE_PREFERENCES = Preferences.userRoot().node("engine")

fun File.ensureExists() {
    if (!exists()) {
        parentFile.mkdirs()
        createNewFile()
    }
}

fun getBuiltinResource(path: String): URL? {
    val classLoader = Thread.currentThread().contextClassLoader
    return classLoader.getResource("defaults/$path")
}

fun listBuiltinResourcesRecursive(path: String): List<Path> {
    val uri = getBuiltinResource(path)!!.toURI()
    val root = when (uri.scheme) {
        "jar" -> {
            val fs = try {
                FileSystems.getFileSystem(uri)
            } catch (_: Exception) {
                FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            }
            fs.getPath("defaults/$path")
        }
        else -> {
            Paths.get(uri)
        }
    }
    return Files.walk(root)
        .filter { Files.isRegularFile(it) }
        .toList()
}

fun loadStdLuaLibrary() {
    if (SharedConstants.DEVELOPER_TEST_ENVIRONMENT && !SharedConstants.LOAD_LUA_LIBRARIES) return
    val targetRoot = BUILTIN_SCRIPTS_DIR
    listBuiltinResourcesRecursive("scripts").forEach { source ->
        println("Loading script ${source.toString()}")
        val relative = source.toString()
            .replace("\\", "/")
            .substringAfter("scripts/")

        val target = targetRoot.resolve(relative).toPath()

        Files.createDirectories(target.parent)
        source.copyTo(target, overwrite = true)
    }
}
fun updateOldFileNaming() {
    val items = ENGINE_DIR.resolve("items")
    if (items.exists()) {
        items.renameTo(ENGINE_DIR.contents)
        CONFIG_LOGGER.warn("Файл старого формата engine/items переименован в engine/contents")
    }
}
