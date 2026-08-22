package org.lain.engine.script

import java.io.File
import java.io.InputStream
import java.net.URL

sealed interface ScriptSource {
    val chunkName: String
    fun open(): InputStream
    fun exists(): Boolean
}

class FileScriptSource(val file: File): ScriptSource {
    override val chunkName: String get() = file.nameWithoutExtension

    override fun open(): InputStream {
        return file.inputStream()
    }
    override fun exists(): Boolean {
        return file.exists()
    }

    override fun toString(): String {
        return file.toString()
    }
}

class ResourceScriptSource(val path: String) : ScriptSource {
    private val resource: URL? = Thread.currentThread()
        .contextClassLoader
        .getResource(path)

    override val chunkName: String get() = File(path).nameWithoutExtension
    override fun open(): InputStream = resource
        ?.openStream()
        ?: error("Module not found: $path")

    override fun exists(): Boolean = resource != null
}