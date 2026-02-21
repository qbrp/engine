package org.lain.engine.storage

import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lain.engine.item.*
import org.lain.engine.util.*
import org.lain.engine.world.Location

fun connectDatabase(server: MinecraftServer): Database {
    val path = server.getSavePath(WorldSavePath.ROOT)
    val oldEngineDbFile = path.toFile().resolve("engine.db")
    if (oldEngineDbFile.exists()) {
        oldEngineDbFile.renameTo(path.toFile().resolve("engine-players.db"))
    }
    val database = Database.connect("jdbc:sqlite:$path/engine-players.db")
    transaction { SchemaUtils.create(ItemsTable) }
    return database
}

object ItemsTable : Table() {
    val uuid = varchar("uuid", 255).uniqueIndex()
    val id = varchar("id", 255)
    val components = binary("components")
}

suspend fun Database.saveItem(item: EngineItem) {
    val persistent = itemPersistentData(item)
    saveItemPersistentData(item.uuid, item.id, persistent)
}

suspend fun Database.loadItem(location: Location, uuid: ItemUuid): EngineItem? {
    val (id, data) = loadPersistentItemData(uuid) ?: return null
    val components = mutableSetOf<Component>()
    var count: Count? = null

    for (component in data.components) {
        when(component) {
            is ItemData.Display -> {
                components.addIfNotNull(component.name)
                components.addIfNotNull(component.tooltip)
                components.addIfNotNull(component.assets)
            }
            is ItemData.Guns -> {
                components.addIfNotNull(component.data)
                components.addIfNotNull(component.display)
            }
            is ItemData.PhysicalParameters -> {
                count = component.count
                components.addIfNotNull(component.mass)
            }
            is ItemData.Equipment -> {
                if (component.hat) components += Hat
            }
            is ItemData.Sounds ->
                components.addIfNotNull(component.data)
            is ItemData.Book ->
                components.add(component.writable ?: component.writableLegacy ?: error("Writeable component doesn't exist"))
            is ItemData.Count -> {
                count = Count(component.value, 16)
            }
            is ItemData.Mass ->
                components.add(Mass(component.value))
        }
    }

    val state = ComponentState(components.toList())

    return itemInstance(uuid, id, location, count ?: Count(1, 1), state)
}

fun dataFixItem(item: EngineItem, storage: NamespacedStorage) {
    if (!item.has<ItemAssets>()) {
        val prefab = storage.items[item.id] ?: return
        val assets = prefab.properties.assets
        item.setNullable(assets)
    }
}

suspend fun Database.saveItemPersistentDataBatch(items: List<Pair<EngineItem, PersistentItemData>>) {
    suspendTransaction(this) {
        ItemsTable.batchUpsert(items) { (item, data) ->
            this[ItemsTable.uuid] = item.uuid.value
            this[ItemsTable.id] = item.id.value
            this[ItemsTable.components] = serializeItemPersistentComponents(data)
        }
    }
}

suspend fun Database.saveItemPersistentData(uuid: ItemUuid, id: ItemId, item: PersistentItemData) {
    suspendTransaction(this) {
        ItemsTable.upsert {
            it[this.uuid] = uuid.value
            it[this.id] = id.value
            it[this.components] = serializeItemPersistentComponents(item)
        }
    }
}

suspend fun Database.loadPersistentItemData(uuid: ItemUuid): Pair<ItemId, PersistentItemData>? {
    return suspendTransaction(this) {
        ItemsTable
            .selectAll()
            .where { ItemsTable.uuid eq uuid.value }
            .map { it[ItemsTable.id] to it[ItemsTable.components] }
            .firstOrNull()
    }?.let { (id, components) ->
        ItemId(id) to PersistentItemData(deserializeItemPersistentComponents(components))
    }
}