package org.lain.engine.storage

import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lain.engine.item.ItemId

fun connectDatabase(server: MinecraftServer): Database {
    val path = server.getWorldPath(LevelResource.ROOT)
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

@Serializable
data class EntityDto(val persistentId: PersistentId, val components: List<ComponentDto>)

suspend fun Database.saveEntitiesBatch(entities: List<EntityDto>) {
    suspendTransaction(this) {
        EcsEntityTable.batchUpsert(entities) { (uuid, components) ->
            this[EcsEntityTable.uuid] = uuid.toString()
            this[EcsEntityTable.components] = serializeEntityComponents(components)
        }
    }
}

suspend fun Database.loadEntity(id: PersistentId): List<ComponentDto>? {
    return suspendTransaction(this) {
        EcsEntityTable
            .selectAll()
            .where { EcsEntityTable.uuid eq id.toString() }
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
        if (!ItemsTable.exists()) return@suspendTransaction null
        ItemsTable
            .selectAll()
            .where { ItemsTable.uuid eq uuid.toString() }
            .map { it[ItemsTable.id] to it[ItemsTable.components] }
            .firstOrNull()
    }?.let { (id, components) ->
        ItemId(id) to PersistentItemData(deserializeItemPersistentComponents(components))
    }
}

class DatabaseEntityProvider(private val database: Database) : EntityProvider {
    override suspend fun loadEntity(persistentId: PersistentId): List<ComponentDto>? {
        return database.loadEntity(persistentId)
    }
}

fun DatabaseEntityResolver(database: Database) = EntityResolver(DatabaseEntityProvider(database))