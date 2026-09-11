package org.lain.engine.script

import org.lain.engine.script.compilation.DiagnosticException

class EngineIdResolution(message: String) : DiagnosticException(message)

fun Module.resolveId(reference: String): EngineId {
    val firstChar = reference.firstOrNull()
        ?: throw EngineIdResolution("Идентификатор пустой")

    return when (firstChar) {
        '/' -> EngineId(reference.drop(1))
        '@' -> {
            val importAlias = reference.drop(1).substringBefore('/')
            val localId = reference.substringAfter('/', missingDelimiterValue = "")

            if (importAlias.isEmpty() || localId.isEmpty()) {
                throw EngineIdResolution(
                    "Некорректная импортированная ссылка: $reference"
                )
            }
            val importedNamespace = imports[importAlias]
                ?: throw EngineIdResolution(
                    "Импорт с псевдонимом @$importAlias не найден"
                )

            EngineId.of(importedNamespace, localId)
        }

        else -> {
            // изначально здесь был EngineId.of, но идентификатор может содержать вложенные пространства имён,
            // так что лучше парсить их
            EngineId("$namespace/$reference")
        }
    }
}