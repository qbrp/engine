package org.lain.engine.storage

import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lain.cyberia.ecs.Component
import org.lain.engine.item.ItemId

fun connectDatabase(server: MinecraftServer): Database {
    val path = server.getSavePath(WorldSavePath.ROOT)
    val oldEngineDbFile = path.toFile().resolve("engine.db")
    if (oldEngineDbFile.exists()) {
        oldEngineDbFile.renameTo(path.toFile().resolve("engine-players.db"))
    }
    val database = Database.connect("jdbc:sqlite:$path/engine-players.db")
    transaction { SchemaUtils.create(EcsEntityTable) }
    return database
}

object EcsEntityTable : Table() {
    val uuid = varchar("uuid", 255).uniqueIndex()
    val components = binary("components")
}

@Deprecated("since 3.6.0")
object ItemsTable : Table() {
    val uuid = varchar("uuid", 255).uniqueIndex()
    val id = varchar("id", 255)
    val components = binary("components")
}

data class EntityDto(val persistentId: PersistentId, val components: List<Component>)

suspend fun Database.saveEntitiesBatch(entities: List<EntityDto>) {
    suspendTransaction(this) {
        EcsEntityTable.batchUpsert(entities) { (uuid, components) ->
            this[EcsEntityTable.uuid] = uuid.value
            this[EcsEntityTable.components] = serializeEntityComponents(components)
        }
    }
}

suspend fun Database.loadEntity(id: PersistentId): List<Component>? {
    return suspendTransaction(this) {
        EcsEntityTable
            .selectAll()
            .where { EcsEntityTable.uuid eq id.value }
            .firstOrNull()
    }?.let {
        deserializeEntityComponents(it[EcsEntityTable.components])
    }
}

/**
 * C 22.04.2026 предметы сохраняются в общую базу данных сущностей
 */
suspend fun Database.loadPersistentItemDataLegacy(uuid: PersistentId): Pair<ItemId, PersistentItemData>? {
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