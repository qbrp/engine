package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.engine.script.compilation.CompilationDiagnostic
import org.lain.engine.script.compilation.DiagnosticContext
import org.lain.engine.script.compilation.DiagnosticException

fun EngineId(string: String) = EngineId.parse(string)

@Serializable
class EngineId private constructor(
    val namespace: String,
    val local: String
) {
    val full
        get() = "$namespace/$local"

    init {
        validate(namespace, local)
    }

    override fun equals(other: Any?): Boolean {
        return other is EngineId && other.namespace == namespace && other.local == local
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + local.hashCode()
        result = 31 * result + full.hashCode()
        return result
    }

    override fun toString(): String {
        return full
    }

    companion object {
        const val UNDEFINED_NAMESPACE = "undefined"

        fun of(namespace: NamespaceId, local: String) = EngineId(namespace.value, local)

        fun validate(namespace: String, local: String) {
            if (!isValidLocal(local)) throw InvalidIdLocalException(local, namespace)
            if (!isValidNamespace(namespace)) throw InvalidIdNamespaceException(namespace, local)
        }

        fun parse(string: String): EngineId {
            val parts = string.split("/")
            val local = parts.last()
            val namespace = when (parts.size != 1) {
                true -> string.dropLast(local.count() + 1)
                false -> UNDEFINED_NAMESPACE
            }
            return EngineId(namespace, local)
        }

        fun fetchLocal(string: String): String {
            return string.substringAfterLast("/")
        }

        fun isValidNamespace(string: String): Boolean {
            return string.none { !validCharNamespace(it) }
        }

        fun isValidLocal(string: String): Boolean {
            return string.none { !validCharLocal(it) }
        }

        private fun validCharNamespace(c: Char): Boolean {
            return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/'
        }

        private fun validCharLocal(c: Char): Boolean {
            return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
        }
    }
}

fun InvalidIdNamespaceException(namespace: String, local: String?) =
    EngineIdentifierException(
        "Пространство имён идентификатора содержит символы, не входящие в перечень разрешенных ( a-z0-9/_- ): $namespace (локальная часть: $local)"
    )

fun InvalidIdLocalException(local: String, namespace: String) =
    EngineIdentifierException(
        "Локальная часть идентификатора содержит символы, не входящие в перечень разрешенных ( a-z0-9_- ): $local (пространство имён: $namespace)"
    )

class EngineIdentifierException(override val message: String) : DiagnosticException(message)