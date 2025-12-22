package org.lain.engine.util
import java.io.File

val ENGINE_DIR = File("engine")
    .also { it.mkdirs() }

fun File.ensureExists() {
    if (!exists()) {
        parentFile.mkdirs()
        createNewFile()
    }
}