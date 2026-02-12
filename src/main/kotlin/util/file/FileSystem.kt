package org.lain.engine.util.file
import java.io.File
import java.net.URL

val ENGINE_DIR = File("engine")
    .also { it.mkdirs() }

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

fun updateOldFileNaming() {
    val items = ENGINE_DIR.resolve("items")
    if (items.exists()) {
        items.renameTo(CONTENTS_DIR)
        CONFIG_LOGGER.warn("Файл старого формата engine/items переименован в engine/contents")
    }
}