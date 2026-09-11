package org.lain.engine.script.yaml

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.player.interaction.ProgressionAnimationId
import org.lain.engine.script.compilation.CompilationDiagnosticLocation
import org.lain.engine.script.compilation.CompilationDiagnosticSeverity
import org.lain.engine.script.compilation.CompilationPhase
import org.lain.engine.script.compilation.CompilationReportBuilder
import org.lain.engine.script.compilation.DiagnosticContext
import org.lain.engine.script.compilation.DiagnosticException
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.compilation.toDiagnostic
import org.lain.engine.util.file.FileSystem
import java.io.File

private const val NAMESPACES_FILENAME = "namespaces.yml"
val DEFAULT_NAMESPACE = NamespaceId("default")

@Serializable
data class ProgressionAnimationConfig(
    val frames: YamlNode? = null,
    val text: String,
    val success: String = text
)

@Serializable
data class FrameIdGeneratorConfig(val name: String, val count: Int)

@Serializable
internal data class NamespaceContents(
    @SerialName("namespace") val id: NamespaceId,
    val items: Map<String, ItemConfig> = mapOf(),
    val sounds: Map<String, SoundEventConfig> = mapOf(),
    @SerialName("progression_animations") val progressionAnimations: Map<String, ProgressionAnimationConfig> = mapOf(),
)

@Serializable
internal data class NamespaceConfig(
    val inherit: NamespaceId? = null,
    val stackable: Boolean? = null,
    @SerialName("stack_size") val maxStackSize: Int? = null,
    val hat: Boolean? = null,
    val model: String = "~/{id}",
    val sounds: Map<String, String> = mapOf(),
    val assets: Map<String, String> = mapOf(),
    val mass: Float? = null,
    @SerialName("progression_animations") val progressionAnimations: Map<String, ProgressionAnimationId> = mapOf(),
)

internal data class YamlNamespace(
    val id: NamespaceId,
    val contents: NamespaceContents,
    val config: NamespaceConfig,
    val sources: Set<File>,
)

internal data class YamlCompilationContext(
    val namespaces: Map<NamespaceId, YamlNamespace>,
    val exceptions: CompilationReportBuilder
)

context(ctx: YamlCompilationContext)
internal fun <T> NamespaceConfig?.computeInheritable(getter: (NamespaceConfig) -> T): T? {
    if (this == null) return null
    val configs = ctx.namespaces
    return getter(this) ?: inherit?.let {
        (configs[it]
            ?: configs[DEFAULT_NAMESPACE])?.config?.computeInheritable(
            getter
        )
    }
}

context(ctx: YamlCompilationContext)
internal fun <K, V> NamespaceConfig.accumulateInheritable(
    getter: (NamespaceConfig) -> Map<K, V>,
    output: MutableMap<K, V> = mutableMapOf(),
) {
    val configs = ctx.namespaces
    if (inherit != null) {
        configs[inherit]?.config?.accumulateInheritable(getter, output) ?: run {
            error("Наследуемая родительская конфигурация не найдена: $inherit")
        }
    }
    output += getter(this)
}

context(ctx: YamlCompilationContext)
internal fun <K, V> NamespaceConfig.accumulateInheritable(getter: (NamespaceConfig) -> Map<K, V>): Map<K, V> {
    val output = mutableMapOf<K, V>()
    accumulateInheritable(getter, output)
    return output
}

internal fun String.replaceToRelative(namespace: YamlNamespace): String {
    return replaceFirst("~", namespace.id.value)
}

private fun loadNamespaces(
    directory: File,
    report: CompilationReportBuilder
): Map<NamespaceId, YamlNamespace> {
    val namespaces = mutableSetOf<NamespaceId>()
    val contents = mutableMapOf<NamespaceId, NamespaceContents>()
    val configs = mutableMapOf<NamespaceId, NamespaceConfig>()
    val sources = mutableMapOf<NamespaceId, MutableSet<File>>()
    FileSystem.ensureDirectory(directory)
    directory.walk().forEach { dir ->
        try {
            if (!dir.isFile || dir.extension != "yml") return@forEach
            if (dir.name == NAMESPACES_FILENAME) {
                val config =
                    Yaml.default.readAsConfig<Map<NamespaceId, NamespaceConfig>>(report, dir)
                        ?: return@forEach
                configs.putAll(config)
            } else {
                val namespace = Yaml.default.readAsConfig<NamespaceContents>(report, dir)
                    ?: return@forEach
                val id = namespace.id
                //TOOD: не забывать дополнять
                val upserted = contents[id]?.let {
                    it.copy(
                        items = it.items + namespace.items,
                        sounds = it.sounds + namespace.sounds,
                        progressionAnimations = it.progressionAnimations + namespace.progressionAnimations
                    )
                }
                contents[id] = upserted ?: namespace
                namespaces.add(id)
                sources.getOrPut(id, ::mutableSetOf).add(dir)
            }
        } catch (e: Exception) {
            val phase = CompilationPhase.COMPILATION
            val location = CompilationDiagnosticLocation(dir.toString())

            when (e) {
                is DiagnosticException -> report.report(
                    e.toDiagnostic(
                        DiagnosticContext(phase, location = location)
                    )
                )

                else -> report.abort(
                    e.toDiagnostic(
                        phase,
                        location = location,
                        severity = CompilationDiagnosticSeverity.FATAL
                    )
                )
            }
        }
    }
    return namespaces.associateWith {
        YamlNamespace(
            it,
            contents[it]!!,
            configs[it] ?: configs[NamespaceId("default")] ?: NamespaceConfig(),
            sources[it]?.toSet() ?: emptySet(),
        )
    }
}

internal fun createYamlCompilationContext(directory: File): YamlCompilationContext {
    val report = CompilationReportBuilder()
    return YamlCompilationContext(loadNamespaces(directory, report), report)
}
/*
internal fun compileContentsYaml(directory: File): Build =
    with(createYamlCompilationContext(directory)) {
        val compiledNamespaces = namespaces.mapNotNull { (id, namespace) ->
            try {
                val contents = namespace.contents
                id to NamespaceDraft(
                    compileItemsYaml(contents.items, namespace)
                        .associateBy { it.id },
                    compileSoundEvents(contents.sounds, namespace)
                        .associateBy { it.id },
                    contents.progressionAnimations.map { (id, animation) ->
                        val framesList = runCatching {
                            Yaml.default.decodeFromYamlNode<List<String>>(
                                animation.frames ?: return@runCatching emptyList()
                            )
                        }
                        val frames = framesList.getOrNull() ?: run {
                            val (baseName, count) = Yaml.default.decodeFromYamlNode<FrameIdGeneratorConfig>(
                                animation.frames!!
                            )
                            List(count) { id -> "$baseName${id + 1}" }
                        }
                        ProgressionAnimationId(
                            EngineId(id)
                        ) to ProgressionAnimation(
                            frames,
                            animation.text,
                            animation.success
                        )
                    }
                        .toMap()
                )
            } catch (e: Exception) {
                val phase = CompilationPhase.COMPILATION

                when (e) {
                    is DiagnosticException -> {
                        exceptions.report(
                            e.toDiagnostic(DiagnosticContext(phase, id))
                        )
                        null
                    }

                    else -> exceptions.fatal(
                        e.toDiagnostic(phase, id, CompilationDiagnosticSeverity.FATAL)
                    )
                }
            }
        }.toMap()

        return Build(
            compiledNamespaces,
            exceptions.build(),
            null,
            listOf(),
            0L
        )
    }
*/
