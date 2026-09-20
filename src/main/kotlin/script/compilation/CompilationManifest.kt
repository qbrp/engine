package org.lain.engine.script.compilation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.engine.script.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class CompilationManifest(
    val success: Boolean,
    val modules: List<CompilationManifestModule>,
    val namespaces: List<CompilationManifestNamespace>,
    val callbacks: List<String>,
    val rootPhase: CompilationManifestPhase?,
    val diagnostics: List<CompilationManifestDiagnostic>,
)

@Serializable
data class CompilationManifestModule(
    val namespace: String,
    val namespaces: List<String>,
    val dependencies: List<String>,
    val imports: Map<String, String>,
)

@Serializable
data class CompilationManifestNamespace(
    val id: String,
    val items: List<String>,
    val sounds: List<String>,
    @SerialName("progression_animations") val progressionAnimations: List<String>,
    val scripts: List<String>,
    val components: List<String>,
    val operations: List<String>,
    val systems: List<CompilationManifestSystem>,
)

@Serializable
data class CompilationManifestSystem(
    val id: String,
    val linked: Boolean,
    val side: String,
    val components: List<CompilationManifestReference>,
)

@Serializable
data class CompilationManifestPhase(
    val name: String,
    val steps: List<CompilationManifestPhaseStep>,
)

@Serializable
sealed class CompilationManifestPhaseStep {
    @Serializable
    data class System(val id: String) : CompilationManifestPhaseStep()

    @Serializable
    data class Phase(val phase: CompilationManifestPhase) : CompilationManifestPhaseStep()
}

@Serializable
data class CompilationManifestReference(
    val id: String,
    val resolved: Boolean,
)

@Serializable
data class CompilationManifestDiagnostic(
    val severity: String,
    val phase: String,
    val message: String,
    val namespace: String? = null,
    val target: CompilationManifestDiagnosticTarget? = null,
    val location: CompilationManifestDiagnosticLocation? = null,
)

@Serializable
data class CompilationManifestDiagnosticTarget(
    val kind: String,
    val id: String,
)

@Serializable
data class CompilationManifestDiagnosticLocation(
    val source: String,
    val line: Int? = null,
    val column: Int? = null,
    val path: String? = null,
)

fun compilationManifestOf(
    outcome: CompilationOutcome,
    buildDraft: BuildDraft?,
    modules: Modules,
    linkedNamespaces: Map<NamespaceId, Namespace> =
        (outcome as? CompilationOutcome.Success)?.build?.namespaces ?: emptyMap(),
): CompilationManifest {
    val draftNamespaces = buildDraft?.namespaces.orEmpty()
    val scriptIds = draftNamespaces.values
        .flatMap { it.scripts.keys }
        .toSet()
    val componentIds = draftNamespaces.values
        .flatMap { it.components.keys }
        .toSet()
    val linkedSystemIds = linkedNamespaces.values
        .flatMap { it.systems.keys }
        .toSet()

    return CompilationManifest(
        success = outcome is CompilationOutcome.Success,
        modules = modules.files.entries
            .sortedWith(
                compareBy(
                    { it.value.namespace.value },
                    { it.key.config.name },
                )
            )
            .map { (_, module) ->
                CompilationManifestModule(
                    namespace = module.namespace.value,
                    namespaces = module.namespaces.map { it.value }.sorted(),
                    dependencies = module.depends.map { it.module.value }.sorted(),
                    imports = module.imports
                        .toSortedMap()
                        .mapValues { (_, namespace) -> namespace.value },
                )
            },
        namespaces = draftNamespaces.entries
            .sortedBy { it.key.value }
            .map { (namespaceId, namespace) ->
                CompilationManifestNamespace(
                    id = namespaceId.value,
                    items = namespace.items.keys.sortedEngineIds(),
                    sounds = namespace.sounds.keys.sortedEngineIds(),
                    progressionAnimations = namespace.progressionAnimations.keys.sortedEngineIds(),
                    scripts = namespace.scripts.keys.sortedEngineIds(),
                    components = namespace.components.keys.sortedEngineIds(),
                    operations = namespace.operations.keys.sortedEngineIds(),
                    systems = namespace.systems.entries
                        .sortedBy { it.key.engineId.full }
                        .map { (id, system) ->
                            CompilationManifestSystem(
                                id = id.engineId.full,
                                linked = id in linkedSystemIds,
                                side = system.side.name.lowercase(),
                                components = system.queryComponents.map { componentId ->
                                    CompilationManifestReference(
                                        id = componentId.engineId.full,
                                        resolved = componentId in componentIds ||
                                                CoreScriptComponents.get(componentId) != null,
                                    )
                                },
                            )
                        },
                )
            },
        callbacks = buildDraft?.callbacks?.keys
            .orEmpty()
            .map { it.id }
            .sorted(),
        rootPhase = buildDraft?.rootPhase?.toCompilationManifestDto(),
        diagnostics = outcome.report.diagnostics.map { diagnostic ->
            CompilationManifestDiagnostic(
                severity = diagnostic.severity.name.lowercase(),
                phase = diagnostic.phase.name.lowercase(),
                message = diagnostic.message,
                namespace = diagnostic.namespace?.value,
                target = diagnostic.target?.let { target ->
                    CompilationManifestDiagnosticTarget(
                        kind = target.kind.name.lowercase(),
                        id = target.localId,
                    )
                },
                location = diagnostic.location?.let { location ->
                    CompilationManifestDiagnosticLocation(
                        source = location.source,
                        line = location.line,
                        column = location.column,
                        path = location.path,
                    )
                },
            )
        },
    )
}

private fun SystemPhaseDraft.toCompilationManifestDto(): CompilationManifestPhase {
    return CompilationManifestPhase(
        name,
        steps.map { step ->
            when (step) {
                is PhaseStepDraft.Phase -> CompilationManifestPhaseStep.Phase(step.phase.toCompilationManifestDto())
                is PhaseStepDraft.System -> CompilationManifestPhaseStep.System(step.system.toString())
            }
        }
    )
}

fun CompilationManifest.writeTo(file: File) {
    val target = file.toPath().toAbsolutePath().normalize()
    val parent = requireNotNull(target.parent) {
        "Compilation manifest path has no parent: $target"
    }
    Files.createDirectories(parent)

    val temporary = Files.createTempFile(parent, "${target.fileName}.", ".tmp")
    try {
        Files.writeString(
            temporary,
            COMPILATION_MANIFEST_JSON.encodeToString(this) + System.lineSeparator(),
            StandardCharsets.UTF_8,
        )
        try {
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun <T : Identifiable> Iterable<T>.sortedEngineIds(): List<String> =
    map { it.engineId.full }.sorted()

private val COMPILATION_MANIFEST_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
}
