package org.lain.engine.util
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