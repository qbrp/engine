package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.ProgressionAnimation
import org.lain.engine.player.ProgressionAnimationId
import org.lain.engine.server.EngineServer
import org.lain.engine.util.Namespace
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.Timestamp
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId
import java.io.File

val CONTENTS_DIR = ENGINE_DIR.resolve("contents")
val DEFAULT_NAMESPACE = NamespaceId("default")
private const val NAMESPACES_FILENAME = "namespaces.yml"

@Serializable
data class ProgressionAnimationConfig(
    val frames: YamlNode,
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

internal data class FileNamespace(
    val id: NamespaceId,
    val contents: NamespaceContents,
    val config: NamespaceConfig,
)

context(ctx: ContentCompileContext)
internal fun <T> NamespaceConfig?.computeInheritable(getter: (NamespaceConfig) -> T): T? {
    if (this == null) return null
    val configs = ctx.namespaces
    return getter(this) ?: inherit?.let { (configs[it] ?: configs[DEFAULT_NAMESPACE])?.config?.computeInheritable(getter) }
}

context(ctx: ContentCompileContext)
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


context(ctx: ContentCompileContext)
internal fun <K, V> NamespaceConfig.accumulateInheritable(getter: (NamespaceConfig) -> Map<K, V>): Map<K, V> {
    val output = mutableMapOf<K, V>()
    accumulateInheritable(getter, output)
    return output
}

fun namespacedId(namespace: NamespaceId, id: String) = "$namespace/$id"

internal fun String.replaceToRelative(namespace: FileNamespace): String {
    return replaceFirst("~", namespace.id.value)
}

private fun loadNamespaces(directory: File = CONTENTS_DIR): Map<NamespaceId, FileNamespace> {
    val namespaces = mutableSetOf<NamespaceId>()
    val contents = mutableMapOf<NamespaceId, NamespaceContents>()
    val configs = mutableMapOf<NamespaceId, NamespaceConfig>()
    directory.ensureExists()
    directory.walk().forEach { dir ->
        if (!dir.isFile && dir.extension != "yml") return@forEach
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
    }
    return namespaces.associateWith {
        FileNamespace(
            it,
            contents[it]!!,
            configs[it] ?: configs[NamespaceId("default")] ?: NamespaceConfig(),
        )
    }
}

fun compileContents(directory: File = CONTENTS_DIR): ContentsCompileResult = with(
    ContentCompileContext(loadNamespaces(directory))
) {
    val start = Timestamp()
    var items = 0
    var sounds = 0
    val namespaces = namespaces.mapValues { (_, namespace) ->
        try {
            val contents = namespace.contents

        CompiledNamespace(
            compileItems(contents.items, namespace)
                .associateBy { it.id }
                .also { items += it.count() },
            compileSoundEvents(contents.sounds, namespace)
                .associateBy { it.id }
                .also { sounds += it.count() },
            contents.progressionAnimations.map { (id, animation) ->
                val framesList = runCatching { Yaml.default.decodeFromYamlNode<List<String>>(animation.frames) }
                val frames = framesList.getOrNull() ?: run {
                    val (baseName, count) = Yaml.default.decodeFromYamlNode<FrameIdGeneratorConfig>(animation.frames)
                    List(count) { id -> "$baseName${id + 1}" }
                }
                ProgressionAnimationId(namespacedId(namespace.id, id)) to ProgressionAnimation(frames, animation.text, animation.success)
            }.toMap()
        )
    } catch (e: Throwable) {
            CONFIG_LOGGER.error("При компиляции пространства имён ${namespace.id} возникла ошибка", e)
            null
        }
    }.filterValues { it != null }

    CONFIG_LOGGER.info(
        "Скомпилировано {} предметов, {} звуковых событий в пространствах имён {} за {} мл.",
        items,
        sounds,
        namespaces.keys.joinToString(separator = ", "),
        start.timeElapsed()
    )

    return ContentsCompileResult(namespaces as Map<NamespaceId, CompiledNamespace>)
}

data class ContentsCompileResult(val namespaces: Map<NamespaceId, CompiledNamespace>)

data class CompiledNamespace(
    val items: Map<ItemId, Item>,
    val sounds: Map<SoundEventId, SoundEvent>,
    val progressionAnimations: Map<ProgressionAnimationId, ProgressionAnimation>
) {
    data class Item(
        val config: ItemConfig,
        val prefab: ItemPrefab,
    ) {
        val id get() = prefab.id
    }
}

internal data class ContentCompileContext(val namespaces: Map<NamespaceId, FileNamespace>)

fun NamespacedStorage.loadContentsCompileResult(result: ContentsCompileResult) {
    upload(
        result.namespaces.map { (id, namespace) ->
            Namespace(
                id,
                Namespace.Holder(namespace.items.mapValues { it.value.prefab }),
                Namespace.Holder(namespace.sounds),
                Namespace.Holder(namespace.progressionAnimations)
            )
        }
    )
}

fun EngineServer.applyContentsCompileResult(result: ContentsCompileResult) {
    namespacedStorage.loadContentsCompileResult(result)
    handler.onContentsUpdate()
}

fun EngineServer.loadContents() {
    val results = compileContents()
    applyContentsCompileResult(results)
}
