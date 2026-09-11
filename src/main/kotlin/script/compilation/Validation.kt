package org.lain.engine.script.compilation

import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespaceId

fun CompilationReportBuilder.reportInvalidNamespace(namespaceId: NamespaceId, id: EngineId, kind: SymbolKind) {
    val invalidNamespace = id.namespace
    report(
        CompilationDiagnostic(
            CompilationDiagnosticSeverity.ERROR,
            "Указано неправильное пространство имён $invalidNamespace идентификатора $id",
            CompilationPhase.VALIDATION,
            target = CompilationDiagnosticTarget(
                kind,
                id.local
            ),
            namespace = namespaceId
        )
    )
}

context(context: CompilationContext)
fun BuildDraft.validateNamespaces() {
    val invalidIds = namespaces.flatMap { (namespaceId, namespace) ->
        namespace.holders.flatMap { holder ->
            holder.keys
                .filterNot { it.engineId.namespace == namespaceId.value }
                .map { namespaceId to it }
        }
    }
    invalidIds.forEach { (namespaceId, id) ->
        context.exceptions.reportInvalidNamespace(namespaceId, id.engineId, id.contentKind)
    }
}