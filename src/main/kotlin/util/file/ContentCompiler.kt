package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.*
import org.lain.engine.mc.ItemEquipment
import org.lain.engine.mc.ItemListTab
import org.lain.engine.mc.ItemProperties
import org.lain.engine.util.Namespace
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Timestamp

val CONTENTS_DIR = ENGINE_DIR.resolve("contents")
private val INVENTORY_TABS = CONTENTS_DIR.resolve(INVENTORY_TABS_FILENAME)
private val NAMESPACES = CONTENTS_DIR.resolve(NAMESPACES_FILENAME)
private const val INVENTORY_TABS_FILENAME = "tabs.yml"
private const val NAMESPACES_FILENAME = "namespaces.yml"

@Serializable
data class NamespaceConfig(
    val stackable: Boolean? = null,
    val hat: Boolean? = null,
    val equip: ItemEquipment? = null,
    val model: String = "~/{id}",
    val sounds: Map<String, SoundEventId> = mapOf()
)

@Serializable
data class NamespaceContents(
    @SerialName("namespace") val id: NamespaceId,
    val items: Map<String, ItemConfig> = mapOf(),
    val sounds: Map<String, SoundEventConfig> = mapOf()
)

@Serializable
data class InventoryTabsConfig(val tabs: Map<String, Entry>) {
    @Serializable
    data class Entry(
        val name: String,
        val icon: String,
        val contents: List<String>
    )
}

fun NamespaceItemId(namespace: NamespaceId, id: String) = ItemId("$namespace/$id")

fun NamespaceSoundEventId(namespace: NamespaceId, id: String) = SoundEventId("$namespace/$id")

fun loadNamespaces(): Map<NamespaceId, NamespaceContents> {
    val namespaceConfigs = mutableMapOf<NamespaceId, NamespaceContents>()
    CONTENTS_DIR.ensureExists()
    CONTENTS_DIR.walk().forEach { dir ->
        if (dir.isFile && dir.extension == "yml" && dir.name != INVENTORY_TABS_FILENAME && dir.name != NAMESPACES_FILENAME) {
            val namespace = Yaml.default.decodeFromStream<NamespaceContents>(dir.inputStream())
            val id = namespace.id
            val upserted = namespaceConfigs[id]?.let {
                it.copy(
                    items = it.items + namespace.items,
                )
            }
            namespaceConfigs[id] = upserted ?: namespace
        }
    }
    return namespaceConfigs
}

fun compileInventoryTabsConfig(namespaces: Map<String, List<ItemProperties>>, items: List<ItemId>): List<ItemListTab> {
    if (!INVENTORY_TABS.exists()) return emptyList()
    val config = Yaml.default.decodeFromStream<InventoryTabsConfig>(INVENTORY_TABS.inputStream())
    return config.tabs.map { (id, entry) ->
        ItemListTab(
            id,
            entry.name,
            entry.contents.flatMap { line ->
                if (line.startsWith("namespace:")) {
                    val namespaceName = line.split(":").getOrNull(1) ?: error("Не указано пространство имён")
                    val namespaceItems = namespaces[namespaceName] ?: error("Пространство имён $namespaceName не найдено")
                    namespaceItems.map { it.id }
                } else {
                    val id = ItemId(line)
                    if (id !in items) {
                        error("Предмет $id не существует")
                    }
                    listOf(id)
                }
            }
        )
    }
}

fun String.replaceToRelative(namespace: NamespaceContents): String {
    return replaceFirst("~", namespace.id.value)
}

fun String.replaceToRelative(namespace: String): String {
    return replaceFirst("~", namespace)
}

data class ContentsCompileResult(
    val items: Map<NamespaceId, List<Item>>,
    val sounds: Map<NamespaceId, List<SoundEvent>>
) {
    data class Item(
        val config: ItemConfig,
        val properties: ItemProperties,
        val prefab: ItemPrefab,
    ) {
        val id get() = prefab.id
    }
}

fun EngineMinecraftServer.applyContentsCompileResult(result: ContentsCompileResult) {
    engine.soundEventStorage.upload(
        result.sounds.map { (namespace, sounds) ->
            Namespace(
                namespace,
                sounds.associateBy { it.id }
            )
        }
    )

    engine.itemPrefabStorage.upload(
        result.items.map { (namespace, entries) ->
            Namespace(
                namespace,
                entries.associate { it.id to it.prefab }
            )
        }
    )

    itemContext.itemPropertiesStorage.upload(
        result.items.map { (namespace, entries) ->
            Namespace(
                namespace,
                entries.associate { it.id to it.properties }
            )
        }
    )

    engine.handler.onContentsUpdate()
}

fun compileContents(namespaces: Map<NamespaceId, NamespaceContents> = loadNamespaces()): ContentsCompileResult {
    val start = Timestamp()
    val namespaceConfigs = if (NAMESPACES.exists()) {
        Yaml.default.decodeFromStream<MutableMap<NamespaceId, NamespaceConfig>>(NAMESPACES.inputStream())
    } else {
        mutableMapOf()
    }
    val defaultNamespace = namespaceConfigs[NamespaceId("default")] ?: NamespaceConfig()

    val sounds = mutableMapOf<NamespaceId, MutableList<SoundEvent>>()
    val items = mutableMapOf<NamespaceId, MutableList<ContentsCompileResult.Item>>()

    for (namespace in namespaces.values) {
        val namespaceConfig = namespaceConfigs.getOrPut(namespace.id) { defaultNamespace }
        for ((itemId, config) in namespace.items) {
            val properties = compileItemProperties(itemId, config, namespace, namespaceConfig)
            items.getOrPut(namespace.id) { mutableListOf() }.add(
                ContentsCompileResult.Item(
                    config,
                    properties,
                    ItemPrefab(
                        ItemInstantiationSettings(
                            properties.id,
                            ItemName(config.displayName),
                            config.gun?.gunComponent(),
                            config.gun?.gunDisplayComponent(),
                            config.tooltip?.let { ItemTooltip(it) },
                            if (config.stackable == true) properties.maxStackSize else null,
                            config.mass?.let { Mass(it) },
                            config.writeable?.let { Writeable(it.pages, listOf(), it.texture) },
                            sounds = config.sounds?.let { component ->
                                ItemSounds(
                                    component.mapValues { SoundEventId(it.value.replaceToRelative(namespace)) }
                                )
                            }
                        )
                    )
                )
            )
        }
        for ((soundId, config) in namespace.sounds) {
            val soundEvent = compileSoundEventConfig(soundId, config, namespace)
            sounds.getOrPut(namespace.id) { mutableListOf() }.add(soundEvent)
        }
    }

    CONFIG_LOGGER.info(
        "Скомпилировано {} предметов, {} звуковых событий в пространствах имён {} за {} мл.",
        items.flatMap { it.value }.count(),
        sounds.flatMap { it.value }.count(),
        namespaces.keys.joinToString(separator = ", "),
        start.timeElapsed()
    )

    return ContentsCompileResult(items.toMap(), sounds.toMap())
}

fun EngineMinecraftServer.loadContents() {
    val results = compileContents(loadNamespaces())
    applyContentsCompileResult(results)
}