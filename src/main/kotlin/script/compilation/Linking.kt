package org.lain.engine.script.compilation

import org.lain.engine.script.*

internal data class LinkingSymbols(
    val scripts: Map<ScriptId, Script<*, *>>,
    val components: Map<ScriptComponentId, ScriptComponentType>,
)

internal fun CompilationReportBuilder.reportLinkingError(
    message: String,
    kind: SymbolKind,
    id: String,
    namespaceId: NamespaceId? = null
) {
    report(
        CompilationDiagnostic(
            CompilationDiagnosticSeverity.ERROR,
            message,
            CompilationPhase.LINKING,
            namespace = namespaceId,
            target = CompilationDiagnosticTarget(kind, id)
        )
    )
}

context(namespaceId: NamespaceId)
internal fun CompilationReportBuilder.reportLinkingError(
    message: String,
    kind: SymbolKind,
    id: String,
) = reportLinkingError(message, kind, id, namespaceId)

context(context: CompilationContext)
internal fun NamespaceDraft.linkedNamespace(
    symbols: LinkingSymbols,
    id: NamespaceId,
): Namespace = with(id) {
    val systems = systems.mapNotNull { (systemId, system) ->
        val query = system.queryComponents.mapNotNull { componentId ->
            symbols.components[componentId]
                ?: CoreScriptComponents.get(componentId)
                ?: run {
                    context.exceptions.reportLinkingError(
                        "Используемый системой компонент $componentId не найден",
                        SymbolKind.SYSTEM,
                        systemId.toString()
                    )
                    null
                }
        }

        if (query.size != system.queryComponents.size) {
            return@mapNotNull null
        }

        systemId to ScriptSystem(
            systemId,
            query,
            system.side,
            system.entityHandleScript
        )
    }.toMap()

    Namespace(
        id = id,
        items = ContentHolder(items),
        sounds = ContentHolder(sounds),
        progressionAnimations = ContentHolder(progressionAnimations),
        scripts = ContentHolder(scripts),
        components = ContentHolder(components),
        operations = ContentHolder(operations),
        systems = ContentHolder(systems)
    )
}

context(context: CompilationContext)
internal fun BuildDraft.linkedNamespaces(): Map<NamespaceId, Namespace> {
    val scripts = namespaces.collect { it.scripts }
    val components = namespaces.collect { it.components }
    val symbols = LinkingSymbols(scripts, components)

    return namespaces.mapValues { (namespaceId, draft) ->
        draft.linkedNamespace(symbols, namespaceId)
    }
}

context(context: CompilationContext)
fun linkedSystemPhase(
    phase: SystemPhaseDraft,
    linkedNamespaces: Map<NamespaceId, Namespace>
): SystemPhase {
    val systems = linkedNamespaces.collect { it.systems }

    fun SystemPhaseDraft.toPhase(): SystemPhase {
        return SystemPhase(
            name,
            steps.mapNotNull {
                when(val draft = it) {
                    is PhaseStepDraft.System -> systems[draft.system]?.let { PhaseStep.System(it) } ?: run {
                        context.exceptions.reportLinkingError(
                            "Составляющая фазу система $it не найдена",
                            SymbolKind.PHASE,
                            it.toString()
                        )
                        null
                    }
                    is PhaseStepDraft.Phase -> PhaseStep.Phase(draft.phase.toPhase())
                }
            },
        )
    }


    return phase.toPhase()
}