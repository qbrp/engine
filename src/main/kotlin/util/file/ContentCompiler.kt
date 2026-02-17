package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.SoundEvent
import org.lain.engine.item.SoundEventId
import org.lain.engine.util.Namespace
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Timestamp

val CONTENTS_DIR = ENGINE_DIR.resolve("contents")
val DEFAULT_NAMESPACE = NamespaceId("default")
private const val NAMESPACES_FILENAME = "namespaces.yml"

@Serializable
internal data class NamespaceContents(
    @SerialName("namespace") val id: NamespaceId,
    val items: Map<String, ItemConfig> = mapOf(),
    val sounds: Map<String, SoundEventConfig> = mapOf()
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

private fun loadNamespaces(): Map<NamespaceId, FileNamespace> {
    val namespaces = mutableSetOf<NamespaceId>()
    val contents = mutableMapOf<NamespaceId, NamespaceContents>()
    val configs = mutableMapOf<NamespaceId, NamespaceConfig>()
    CONTENTS_DIR.ensureExists()
    CONTENTS_DIR.walk().forEach { dir ->
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

fun compileContents(): ContentsCompileResult = with(ContentCompileContext(loadNamespaces())) {
    val start = Timestamp()
    var items = 0
    var sounds = 0
    val namespaces = namespaces.mapValues { (_, namespace) ->
        val contents = namespace.contents

        CompiledNamespace(
            compileItems(contents.items, namespace)
                .associateBy { it.id }
                .also { items += it.count() },
            compileSoundEvents(contents.sounds, namespace)
                .associateBy { it.id }
                .also { sounds += it.count() }
        )
    }

    CONFIG_LOGGER.info(
        "Скомпилировано {} предметов, {} звуковых событий в пространствах имён {} за {} мл.",
        items,
        sounds,
        namespaces.keys.joinToString(separator = ", "),
        start.timeElapsed()
    )

    return ContentsCompileResult(namespaces)
}

data class ContentsCompileResult(val namespaces: Map<NamespaceId, CompiledNamespace>)

data class CompiledNamespace(
    val items: Map<ItemId, Item>,
    val sounds: Map<SoundEventId, SoundEvent>
) {
    data class Item(
        val config: ItemConfig,
        val prefab: ItemPrefab,
    ) {
        val id get() = prefab.id
    }
}

internal data class ContentCompileContext(val namespaces: Map<NamespaceId, FileNamespace>)

fun EngineMinecraftServer.applyContentsCompileResult(result: ContentsCompileResult) {
    engine.namespacedStorage.upload(
        result.namespaces.map { (id, namespace) ->
            Namespace(
                id,
                namespace.items.mapValues { it.value.prefab },
                namespace.sounds
            )
        }
    )
    engine.handler.onContentsUpdate()
}


fun EngineMinecraftServer.loadContents() {
    val results = compileContents()
    applyContentsCompileResult(results)
}
