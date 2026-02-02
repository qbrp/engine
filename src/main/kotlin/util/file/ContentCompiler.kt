package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.entity.EquipmentSlot
import net.minecraft.util.Identifier
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.Barrel
import org.lain.engine.item.Gun
import org.lain.engine.item.GunDisplay
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemInstantiationProperties
import org.lain.engine.item.ItemName
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemSounds
import org.lain.engine.item.ItemTooltip
import org.lain.engine.item.SoundEvent
import org.lain.engine.item.SoundEventId
import org.lain.engine.item.SoundId
import org.lain.engine.mc.ItemProperties
import org.lain.engine.mc.ItemEquipment
import org.lain.engine.mc.ItemListTab
import org.lain.engine.util.EngineId
import org.lain.engine.util.Namespace
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Timestamp
import kotlin.collections.iterator
import kotlin.collections.map

private val CONTENTS_DIR = ENGINE_DIR.resolve("contents")
private val INVENTORY_TABS = CONTENTS_DIR.resolve(INVENTORY_TABS_FILENAME)
private val NAMESPACES = CONTENTS_DIR.resolve(NAMESPACES_FILENAME)
private const val INVENTORY_TABS_FILENAME = "tabs.yml"
private const val NAMESPACES_FILENAME = "namespaces.yml"

@Serializable
data class NamespaceConfig(
    val stackable: Boolean? = null,
    val hat: Boolean? = null,
    val equip: ItemEquipment? = null,
    val model: String = "~/{id}"
)

@Serializable
data class NamespaceContents(
    @SerialName("namespace") val id: String,
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

fun NamespaceItemId(namespace: String, id: String) = ItemId("$namespace/$id")

fun NamespaceSoundEventId(namespace: String, id: String) = SoundEventId("$namespace/$id")

fun loadNamespaces(): Map<String, NamespaceContents> {
    val namespaceConfigs = mutableMapOf<String, NamespaceContents>()
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
    return replaceFirst("~", namespace.id)
}

fun String.replaceToRelative(namespace: String): String {
    return replaceFirst("~", namespace)
}

fun EngineMinecraftServer.compileContents(namespaces: Map<String, NamespaceContents> = loadNamespaces()) {
    val start = Timestamp()
    val namespaceConfigs = if (NAMESPACES.exists()) {
        Yaml.default.decodeFromStream<MutableMap<String, NamespaceConfig>>(NAMESPACES.inputStream())
    } else {
        mutableMapOf()
    }
    val defaultNamespace = namespaceConfigs["default"] ?: NamespaceConfig()

    val sounds = mutableMapOf<String, MutableList<SoundEvent>>()
    val items = mutableMapOf<String, MutableList<Pair<ItemConfig, ItemProperties>>>()

    for (namespace in namespaces.values) {
        val namespaceConfig = namespaceConfigs.getOrPut(namespace.id) { defaultNamespace }
        for ((itemId, config) in namespace.items) {
            val item = compileItemConfig(itemId, config, namespace, namespaceConfig)
            items.getOrPut(namespace.id) { mutableListOf() }.add(config to item)
        }

        for ((soundId, config) in namespace.sounds) {
            val soundEvent = compileSoundEventConfig(soundId, config, namespace)
            sounds.getOrPut(namespace.id) { mutableListOf() }.add(soundEvent)
        }
    }

    engine.soundEventStorage.upload(
        sounds.map { (namespace, sounds) ->
            Namespace(
                NamespaceId(namespace),
            sounds.associateBy { it.id }
            )
        }
    )

    engine.itemPrefabStorage.upload(
        items.map { (namespace, entries) ->
            Namespace(
                NamespaceId(namespace),
                entries.associate { (config, properties) ->
                    properties.id to ItemPrefab(
                        ItemInstantiationProperties(
                            properties.id,
                            ItemName(config.displayName),
                            config.gun?.gunComponent(),
                            config.gun?.gunDisplayComponent(),
                            config.tooltip?.let { ItemTooltip(it) },
                            sounds = config.sounds?.let { component ->
                                ItemSounds(
                                    component.mapValues { SoundEventId(it.value.replaceToRelative(namespace)) }
                                )
                            }
                        )
                    )
                }
            )
        }
    )
    itemContext.itemPropertiesStorage.upload(
        items.map { (namespace, entries) ->
            Namespace(
                NamespaceId(namespace),
                entries.associate { (config, properties) -> properties.id to properties }
            )
        }
    )

    CONFIG_LOGGER.info(
        "Скомпилировано {} предметов, {} звуковых событий в пространствах имён {} за {} мл.",
        items.flatMap { it.value }.count(),
        sounds.flatMap { it.value }.count(),
        namespaces.keys.joinToString(separator = ", "),
        start.timeElapsed()
    )
}

fun EngineMinecraftServer.compileItemsCatching() {
    try {
        compileContents(loadNamespaces())
    } catch (e: Throwable) {
        CONFIG_LOGGER.error("При компиляции предметов возникла ошибка", e)
    }
}