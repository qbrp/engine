package org.lain.engine.script.compilation

import org.lain.engine.item.ItemId
import org.lain.engine.script.Identifiable
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptSystemId
import org.lain.engine.util.OperationId
import org.lain.engine.world.SoundEventId
import org.slf4j.Logger

internal class CompilationAbortException : RuntimeException(null, null, false, false)

fun Throwable.toDiagnostic(
    phase: CompilationPhase,
    namespace: NamespaceId? = null,
    severity: CompilationDiagnosticSeverity = CompilationDiagnosticSeverity.ERROR,
    message: String = this.message ?: "Неизвестная ошибка",
    location: CompilationDiagnosticLocation? = null,
) = CompilationDiagnostic(
    severity = severity,
    message = message,
    phase = phase,
    cause = this,
    location = location,
    namespace = namespace,
)

data class DiagnosticContext(
    val phase: CompilationPhase,
    val namespace: NamespaceId? = null,
    val location: CompilationDiagnosticLocation? = null,
)

open class DiagnosticException(
    message: String? = null,
    caused: Exception? = null,
    val severity: CompilationDiagnosticSeverity = CompilationDiagnosticSeverity.ERROR,
) : RuntimeException(message, caused) {
    open fun toDiagnostic(context: DiagnosticContext) =
        toDiagnostic(
            phase = context.phase,
            location = context.location,
            severity = severity,
            namespace = context.namespace,
            message = message ?: "Неизвестная ошибка"
        )
}

enum class CompilationDiagnosticSeverity {
    WARNING,
    ERROR,
    FATAL;

    val isError: Boolean
        get() = this == ERROR || this == FATAL
}

enum class CompilationPhase {
    INSTALL,
    COMPILATION,
    VALIDATION,
    LINKING
}

data class CompilationDiagnosticLocation(
    val source: String,
    val line: Int? = null,
    val column: Int? = null,
    val path: String? = null
) {
    init {
        require(source.isNotBlank()) { "Diagnostic source cannot be blank" }
        require(line == null || line > 0) { "Diagnostic line must be positive" }
        require(column == null || column > 0) { "Diagnostic column must be positive" }
    }

    fun format(): String = buildString {
        append(source)
        line?.let {
            append(':')
            append(it)
            column?.let { column ->
                append(':')
                append(column)
            }
        }
        path?.takeUnless { it == "<root>" }?.let {
            append(" [")
            append(it)
            append(']')
        }
    }
}

@JvmInline
value class SymbolKind(val name: String) {
    companion object {
        val ITEM = SymbolKind("item")
        val SOUND = SymbolKind("sound")
        val SCRIPT = SymbolKind("script")
        val COMPONENT = SymbolKind("component")
        val OPERATION = SymbolKind("operation")
        val SYSTEM = SymbolKind("system")
        val PHASE = SymbolKind("phase")
        val OTHER = SymbolKind("other")
    }
}
val Identifiable.contentKind
    get() = when (this) {
        is ScriptComponentId -> SymbolKind.COMPONENT
        is ItemId -> SymbolKind.ITEM
        is SoundEventId -> SymbolKind.SOUND
        is OperationId -> SymbolKind.OPERATION
        is ScriptSystemId -> SymbolKind.SYSTEM
        else -> SymbolKind.OTHER
    }

data class CompilationDiagnosticTarget(
    val kind: SymbolKind,
    val localId: String
)

data class CompilationDiagnostic(
    val severity: CompilationDiagnosticSeverity,
    val message: String,
    val phase: CompilationPhase,
    val namespace: NamespaceId? = null,
    val location: CompilationDiagnosticLocation? = null,
    val target: CompilationDiagnosticTarget? = null,
    val cause: Throwable? = null
) {
    init {
        require(message.isNotBlank()) { "Diagnostic message cannot be blank" }
    }

    val isError: Boolean
        get() = severity.isError

    fun format(): String = buildString {
        namespace?.let {
            append('[')
            append(it)
            append(']')
        }
        target?.let {
            append('[')
            append(it.kind.name.lowercase())
            append(":")
            append(it.localId)
            append("]")
        }
        append(' ')
        location?.let {
            append(it.format())
            append(": ")
        }
        append(message)
    }
}

data class CompilationReport(
    val diagnostics: List<CompilationDiagnostic> = emptyList(),
) {
    val errors: List<CompilationDiagnostic>
        get() = diagnostics.filter { it.isError }

    val warnings: List<CompilationDiagnostic>
        get() = diagnostics.filter { it.severity == CompilationDiagnosticSeverity.WARNING }

    val hasErrors: Boolean
        get() = diagnostics.any { it.isError }

    operator fun plus(other: CompilationReport): CompilationReport =
        CompilationReport(diagnostics + other.diagnostics)

    companion object {
        val EMPTY = CompilationReport()
    }
}

fun interface CompilationDiagnosticHandler {
    fun handle(diagnostic: CompilationDiagnostic)
}

fun CompilationReport.handleWith(handler: CompilationDiagnosticHandler) {
    diagnostics.forEach(handler::handle)
}

class CompilationReportBuilder(
    initialReport: CompilationReport = CompilationReport.EMPTY
) {
    private val diagnostics = initialReport.diagnostics.toMutableList()

    fun abort(diagnostic: CompilationDiagnostic): Nothing {
        require(diagnostic.severity == CompilationDiagnosticSeverity.FATAL)
        report(diagnostic)
        throw CompilationAbortException()
    }

    fun report(diagnostic: CompilationDiagnostic) {
        diagnostics += diagnostic
    }

    fun report(report: CompilationReport) {
        diagnostics += report.diagnostics
    }

    fun build(): CompilationReport = CompilationReport(
        diagnostics.sortedByDescending { it.severity.ordinal }
    )
}

fun CompilationReport.logTo(logger: Logger) {
    handleWith { diagnostic ->
        val message = diagnostic.format()
        when (diagnostic.severity) {
            CompilationDiagnosticSeverity.WARNING -> logger.warn(message, diagnostic.cause)
            CompilationDiagnosticSeverity.ERROR,
            CompilationDiagnosticSeverity.FATAL -> logger.error(message, diagnostic.cause)
        }
    }
}
