package org.lain.engine.script

import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

sealed class NamespaceHashMapValidationResult {
    object Success : NamespaceHashMapValidationResult()
    data class Error(val missing: List<String>, val invalid: List<String>) : NamespaceHashMapValidationResult() {
        fun computeErrorMessage(): String {
            val errorString = StringBuilder("<bold>Скрипты сервера отличаются от ваших</bold>")
            if (missing.isNotEmpty()) {
                errorString.append("<newline>Отсутствуют: ")
                errorString.append(missing.joinToString())
            }

            if (invalid.isNotEmpty()) {
                errorString.append("<newline>Отличаются: ")
                errorString.append(invalid.joinToString())
            }
            return errorString.toString()
        }
    }
}

fun validateNamespaceHashMap(playerNamespaceHashMap: NamespaceHashMap, serverNamespaceHashMap: NamespaceHashMap): NamespaceHashMapValidationResult {
    val missing = mutableListOf<String>()
    val invalid = mutableListOf<String>()

    for ((id, serverHash) in serverNamespaceHashMap) {
        val clientHash = playerNamespaceHashMap[id]

        when {
            clientHash == null -> missing += id.value
            clientHash != serverHash -> invalid += id.value
        }
    }
    return if (missing.isNotEmpty() || invalid.isNotEmpty()) {
        NamespaceHashMapValidationResult.Error(missing, invalid)
    } else {
        NamespaceHashMapValidationResult.Success
    }
}