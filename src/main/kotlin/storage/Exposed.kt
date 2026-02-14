package org.lain.engine.storage

import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lain.engine.item.*
import org.lain.engine.util.Component
import org.lain.engine.util.ComponentState
import org.lain.engine.world.Location

fun connectDatabase(server: MinecraftServer): Database {
    val path = server.getSavePath(WorldSavePath.ROOT)
    val database = Database.connect("jdbc:sqlite:$path/engine.db")
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
    val components = mutableListOf<Component>()

    for (component in data.components) {
        when(component) {
            is ItemData.Display -> {
                components.addIfNotNull(component.name)
                components.addIfNotNull(component.tooltip)
            }
            is ItemData.Guns -> {
                components.addIfNotNull(component.data)
                components.addIfNotNull(component.display)
            }
            is ItemData.Sounds -> {
                components.addIfNotNull(component.data)
            }
            is ItemData.Count -> {
                components.addIfNotNull(Count(component.value))
            }
            is ItemData.Mass ->
                components.addIfNotNull(Mass(component.value))
        }
    }

    val state = ComponentState(components)

    return itemInstance(uuid, id, location, state)
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