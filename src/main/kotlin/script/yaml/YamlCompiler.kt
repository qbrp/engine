package org.lain.engine.script.yaml

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.player.ProgressionAnimation
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.file.ensureExists
import org.lain.engine.world.SoundEventId
import java.io.File

private const val NAMESPACES_FILENAME = "namespaces.yml"

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
    val sounds: Map<String, SoundEventId> = mapOf(),
    val assets: Map<String, String> = mapOf(),
    val mass: Float? = null,
    @SerialName("progression_animations") val progressionAnimations: Map<String, ProgressionAnimationId> = mapOf(),
)

internal data class YamlNamespace(
    val id: NamespaceId,
    val contents: NamespaceContents,
    val config: NamespaceConfig,
)

internal data class YamlCompilationContext(
    val namespaces: Map<NamespaceId, YamlNamespace>,
    val errors: MutableList<Throwable>
)

context(ctx: YamlCompilationContext)
internal fun <T> NamespaceConfig?.computeInheritable(getter: (NamespaceConfig) -> T): T? {
    if (this == null) return null
    val configs = ctx.namespaces
    return getter(this) ?: inherit?.let { (configs[it] ?: configs[_root_ide_package_.org.lain.engine.script.DEFAULT_NAMESPACE])?.config?.computeInheritable(getter) }
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

fun namespacedId(namespace: NamespaceId, id: String) = "$namespace/$id"

internal fun String.replaceToRelative(namespace: YamlNamespace): String {
    return replaceFirst("~", namespace.id.value)
}

private fun loadNamespaces(directory: File, errors: MutableList<Throwable>): Map<NamespaceId, YamlNamespace> {
    val namespaces = mutableSetOf<NamespaceId>()
    val contents = mutableMapOf<NamespaceId, NamespaceContents>()
    val configs = mutableMapOf<NamespaceId, NamespaceConfig>()
    directory.ensureExists()
    directory.walk().forEach { dir ->
        try {
            if (!dir.isFile || dir.extension != "yml") return@forEach
            if (dir.name == NAMESPACES_FILENAME) {
                val config = Yaml.default.decodeFromStream<Map<NamespaceId, NamespaceConfig>>(dir.inputStream())
                configs.putAll(config)
            } else {
                val namespace = Yaml.default.decodeFromStream<NamespaceContents>(dir.inputStream())
                val id = namespace.id
                val upserted = contents[id]?.let {
                    it.copy(items = it.items + namespace.items)
                }
                contents[id] = upserted ?: namespace
                namespaces.add(id)
            }
        } catch (e: Throwable) {
            _root_ide_package_.org.lain.engine.script.logNamespaceCompilationError(NamespaceId(dir.name), e)
            errors += e
        }
    }
    return namespaces.associateWith {
        YamlNamespace(
            it,
            contents[it]!!,
            configs[it] ?: configs[NamespaceId("default")] ?: NamespaceConfig(),
        )
    }
}

internal fun createYamlCompilationContext(directory: File): YamlCompilationContext {
    val errors = mutableListOf<Throwable>()
    return YamlCompilationContext(loadNamespaces(directory, errors), errors)
}

internal fun compileContentsYaml(directory: File): org.lain.engine.script.CompilationResult = with(createYamlCompilationContext(directory)) {
    val namespaces = namespaces.mapValues { (_, namespace) ->
        try {
            val contents = namespace.contents
            _root_ide_package_.org.lain.engine.script.CompiledNamespace(
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
                        val (baseName, count) = Yaml.default.decodeFromYamlNode<FrameIdGeneratorConfig>(animation.frames!!)
                        List(count) { id -> "$baseName${id + 1}" }
                    }
                    ProgressionAnimationId(namespacedId(namespace.id, id)) to ProgressionAnimation(
                        frames,
                        animation.text,
                        animation.success
                    )
                }
                    .toMap()
            )
        } catch (e: Throwable) {
            _root_ide_package_.org.lain.engine.script.logNamespaceCompilationError(namespace.id, e)
            errors += e
            null
        }
    }.filterValues { it != null }

    return _root_ide_package_.org.lain.engine.script.CompilationResult(
        namespaces as Map<NamespaceId, org.lain.engine.script.CompiledNamespace>,
        errors
    )
}