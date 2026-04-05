package org.lain.engine.storage

import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lain.cyberia.ecs.Component
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemUuid
import org.lain.engine.util.component.ComponentState

fun connectDatabase(server: MinecraftServer): Database {
    val path = server.getSavePath(WorldSavePath.ROOT)
    val oldEngineDbFile = path.toFile().resolve("engine.db")
    if (oldEngineDbFile.exists()) {
        oldEngineDbFile.renameTo(path.toFile().resolve("engine-players.db"))
    }
    val database = Database.connect("jdbc:sqlite:$path/engine-players.db")
    transaction { SchemaUtils.create(ItemsTable, EcsEntityTable) }
    return database
}

object EcsEntityTable : Table() {
    val uuid = varchar("uuid", 255).uniqueIndex()
    val components = binary("components")
}

object ItemsTable : Table() {
    val uuid = varchar("uuid", 255).uniqueIndex()
    val id = varchar("id", 255)
    val components = binary("components")
}

suspend fun Database.saveEntitiesBatch(entities: List<Pair<PersistentId, ComponentState>>) {
    suspendTransaction(this) {
        EcsEntityTable.batchUpsert(entities) { (uuid, data) ->
            this[EcsEntityTable.uuid] = uuid.uuid
            this[EcsEntityTable.components] = serializeEntityComponents(data.getComponents())
        }
    }
}

suspend fun Database.loadEntity(id: PersistentId): List<Component>? {
    return suspendTransaction(this) {
        EcsEntityTable
            .selectAll()
            .where { EcsEntityTable.uuid eq id.uuid }
            .firstOrNull()
    }?.let {
        deserializeEntityComponents(it[EcsEntityTable.components])
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