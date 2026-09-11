package org.lain.engine.client.handler

import org.lain.engine.script.compilation.CompilationFailedException

val CompilationFailedException.disconnectText: String
    get() {
        return """
            Не удалось скомпилировать ресурсы Engine, чтобы зайти на сервер
            ${report.errors.joinToString(separator = "<newline>") { it.format() }}
        """.trimIndent()
    }