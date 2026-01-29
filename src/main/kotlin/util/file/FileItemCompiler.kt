package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.entity.EquipmentSlot
import net.minecraft.util.Identifier
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemName
import org.lain.engine.item.ItemNamespace
import org.lain.engine.item.ItemNamespaceId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.mc.ItemProperties
import org.lain.engine.mc.ItemEquipment
import org.lain.engine.mc.ItemListTab
import org.lain.engine.util.EngineId
import org.lain.engine.util.text.parseMiniMessage
import kotlin.collections.iterator
import kotlin.collections.map

private val ITEMS_DIR = ENGINE_DIR.resolve("items")
private val INVENTORY_TABS = ITEMS_DIR.resolve(INVENTORY_TABS_FILENAME)
private val NAMESPACES = ITEMS_DIR.resolve(NAMESPACES_FILENAME)
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
data class ItemNamespaceConfig(
    @SerialName("namespace") val id: String,
    val items: Map<String, ItemConfig>
)

@Serializable
data class ItemConfig(
    @SerialName("display_name") val displayName: String,
    val material: String = "stick",
    val model: String? = null,
    val texture: String? = null,
    @SerialName("asset") val assetType: AssetType = AssetType.FILE,
    val stackable: Boolean = true,
    @SerialName("stack_size") val maxStackSize: Int = 16,
    val equip: ItemEquipment? = null,
    val hat: Boolean? = null
) {
    enum class AssetType {
        GENERATED, FILE
    }
}

data class CompileItems(val namespaces: Map<String, ItemNamespaceConfig>)

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

fun deserializeCompilingItems(): CompileItems {
    val namespaceConfigs = mutableMapOf<String, ItemNamespaceConfig>()
    ITEMS_DIR.ensureExists()
    ITEMS_DIR.walk().forEach { dir ->
        if (dir.isFile && dir.extension == "yml" && dir.name != INVENTORY_TABS_FILENAME && dir.name != NAMESPACES_FILENAME) {
            val namespace = Yaml.default.decodeFromStream<ItemNamespaceConfig>(dir.inputStream())
            val id = namespace.id
            val toPut = namespaceConfigs[id]?.let { it.copy(items = it.items + namespace.items) } ?: namespace
            namespaceConfigs[id] = toPut
        }
    }
    return CompileItems(namespaceConfigs)
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

fun EngineMinecraftServer.compileItems(compile: CompileItems = deserializeCompilingItems()) {
    val itemsToAdd = mutableListOf<ItemProperties>()
    val namespaces = if (NAMESPACES.exists()) {
        Yaml.default.decodeFromStream<MutableMap<String, NamespaceConfig>>(NAMESPACES.inputStream())
    } else {
        mutableMapOf()
    }
    val defaultNamespace = namespaces["default"] ?: NamespaceConfig()
    val namespaceToItemMap = mutableMapOf<String, MutableList<ItemProperties>>()
    val items = mutableListOf<Pair<ItemConfig, ItemProperties>>()

    for (namespace in compile.namespaces.values) {
        fun String.replaceToRelative(): String {
            return replaceFirst("~", namespace.id)
        }

        for ((idString, config) in namespace.items) {
            val id = NamespaceItemId(namespace.id, idString)
            val material = Identifier.ofVanilla(config.material.lowercase())
            val namespaceConfig = namespaces.getOrPut(namespace.id) { defaultNamespace }
            val cfgMaxStackSize = config.maxStackSize
            val cfgStackable = namespaceConfig.stackable ?: config.stackable
            val stackSize = if (cfgStackable) cfgMaxStackSize else 1
            val asset = when(config.assetType) {
                ItemConfig.AssetType.FILE -> config.model?.replaceToRelative() ?: namespaceConfig.model
                    .replaceToRelative()
                    .replace("{id}", idString)
                ItemConfig.AssetType.GENERATED -> config.texture ?: id.value
            }
            val assetId = EngineId(asset)

            val equip = (config.equip ?: namespaceConfig.equip) ?: (config.hat ?: namespaceConfig.hat)?.let { if (it) ItemEquipment(EquipmentSlot.HEAD) else null }

            itemsToAdd += ItemProperties(
                id,
                material,
                assetId,
                stackSize,
                equip
            ).also {
                items.add(config to it)
                namespaceToItemMap.getOrPut(namespace.id) { mutableListOf() }.add(it)
            }
        }
    }

    engine.itemPrefabStorage.load(
        items.map { (config, properties) ->
            ItemPrefab(properties.id, ItemName(config.displayName))
        }
    )
    itemContext.itemRegistry.upload(
        itemsToAdd,
        namespaces.map {
            ItemNamespace(
                ItemNamespaceId(it.key),
                namespaceToItemMap[it.key]?.map { item -> item.id } ?: listOf()
            )
        }
    )
    itemContext.tabs = compileInventoryTabsConfig(namespaceToItemMap, itemsToAdd.map { it.id })
    CONFIG_LOGGER.info(
        "Скомпилировано {} предметов в пространствах имён {}",
        itemsToAdd.count(),
        namespaceToItemMap.keys.joinToString(separator = ", ")
    )
}

fun EngineMinecraftServer.compileItemsCatching(compile: CompileItems = deserializeCompilingItems()) {
    try {
        compileItems(compile)
    } catch (e: Throwable) {
        CONFIG_LOGGER.error("При компиляции предметов возникла ошибка", e)
    }
}