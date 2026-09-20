package org.lain.engine.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lain.engine.script.EngineId
import org.lain.engine.util.ecs.RelationTypeId
import org.lain.engine.util.ecs.toRelationTypeId
import kotlin.io.path.pathString

fun connectDatabase(server: MinecraftServer): Database {
    val path = server.getWorldPath(LevelResource.ROOT)
    return connectDatabase(path.pathString)
}

fun connectDatabase(path: String): Database {
    val database = Database.connect("jdbc:sqlite:$path/engine.db")
    transaction { SchemaUtils.create(EntityTable, ComponentsTable, RelationsTable, ItemsTable) }
    return database
}

object EntityTable : Table() {
    val uuid = varchar("uuid", 255)
    val kind = enumeration<EntityDatabaseKind>("kind")

    override val primaryKey = PrimaryKey(uuid)
}

object ComponentsTable : Table() {
    val entity = reference("entity", EntityTable.uuid)
    val id = varchar("id", 255)
    val version = integer("version")
    val component = blob("component")
    val error = text("error").nullable()

    init {
        uniqueIndex(entity, id)
    }
}

object RelationsTable : Table() {
    val parent = reference("parent", EntityTable.uuid)
    val child = reference("child", EntityTable.uuid)
    val id = varchar("id", 255)

    override val primaryKey = PrimaryKey(parent, child, id)

    init {
        uniqueIndex(child, id)
    }
}

object ItemsTable : Table() {
    val uuid = reference("uuid", EntityTable.uuid)
    val prefabId = varchar("prefab_id", 255)
    val count = integer("count")
    val maxCount = integer("count")

    override val primaryKey = PrimaryKey(uuid)
}

data class EntityPersistenceDataBatchDto(
    val items: List<Pair<PersistentId, EntityPersistenceData.Item>>
)

data class EntityBatchDto(
    val entityId: PersistentId,
    val kind: EntityDatabaseKind
)

data class ComponentBatchDto(
    val entityId: PersistentId,
    val record: ComponentPersistentRecord
)

data class RelationBatchDto(
    val id: RelationTypeId,
    val parent: PersistentId,
    val child: PersistentId,
)

suspend fun Database.upsertComponentsBatchTransaction(components: List<ComponentBatchDto>) {
    suspendTransaction(this) {
        upsertComponentsBatch(components)
    }
}

suspend fun upsertComponentsBatch(components: List<ComponentBatchDto>) = withContext(Dispatchers.IO) {
    ComponentsTable.batchUpsert(
        components,
        shouldReturnGeneratedValues = false
    ) {
        this[ComponentsTable.entity] = it.entityId.toString()
        this[ComponentsTable.id] = it.record.id.toString()
        this[ComponentsTable.version] = it.record.version
        this[ComponentsTable.component] = ExposedBlob(it.record.payload)
        this[ComponentsTable.error] = it.record.error
    }
}

suspend fun Database.saveEntitiesBatch(
    entities: List<EntityBatchDto>,
    data: EntityPersistenceDataBatchDto,
    components: List<ComponentBatchDto>,
    ownerships: List<RelationBatchDto>,
) {
    if (entities.isEmpty()) return
    val entitiesIds = entities.map { it.entityId.toString() }

    suspendTransaction(this) {
        EntityTable.batchUpsert(
            entities,
            shouldReturnGeneratedValues = false
        ) {
            this[EntityTable.uuid] = it.entityId.toString()
            this[EntityTable.kind] = it.kind
        }

        if (data.items.isNotEmpty()) {
            ItemsTable.batchUpsert(data.items, shouldReturnGeneratedValues = false) {
                val (entityId, item) = it
                this[ItemsTable.uuid] = entityId.toString()
                this[ItemsTable.prefabId] = item.prefabId.toString()
                this[ItemsTable.count] = item.count
                this[ItemsTable.maxCount] = item.maxCount
            }
        }

        ComponentsTable.deleteWhere {
            entity inList entitiesIds and (error eq null)
        }
        RelationsTable.deleteWhere {
            (parent inList entitiesIds)
        }

        upsertComponentsBatch(components)

        RelationsTable.batchInsert(
            ownerships,
            shouldReturnGeneratedValues = false
        ) {
            this[RelationsTable.id] = it.id.toString()
            this[RelationsTable.parent] = it.parent.toString()
            this[RelationsTable.child] = it.child.toString()
        }
    }
}

suspend fun Database.saveEntity(
    kind: EntityDatabaseKind,
    entity: EntityPersistentRecord,
) {
    saveEntitiesBatch(
        entities = listOf(EntityBatchDto(entity.uuid, kind)),
        data = EntityPersistenceDataBatchDto(
            items = when (val data = entity.data) {
                is EntityPersistenceData.Item -> listOf(entity.uuid to data)
                else -> emptyList()
            }
        ),
        components = entity.components.map { ComponentBatchDto(entity.uuid, it) },
        ownerships = entity.relations.map {
            RelationBatchDto(it.id, entity.uuid, it.child)
        },
    )
}

suspend fun Database.loadEntity(id: PersistentId): EntityPersistentRecord? {
    return suspendTransaction(this) {
        val entityRow = EntityTable.selectAll()
            .where { EntityTable.uuid eq id.toString() }
            .firstOrNull() ?: return@suspendTransaction null
        val components = ComponentsTable.selectAll()
            .where { ComponentsTable.entity eq id.toString() }
            .map {
                ComponentPersistentRecord(
                    it[ComponentsTable.id].asRawEngineId(),
                    it[ComponentsTable.version],
                    it[ComponentsTable.component].bytes,
                    it[ComponentsTable.error]
                )
            }
        val ownerships = RelationsTable.selectAll()
            .where { RelationsTable.parent eq id.toString() }
            .map {
                RelationPersistentRecord(
                    EngineId(it[RelationsTable.id]).toRelationTypeId(),
                    Uuid.from(it[RelationsTable.child])
                )
            }

        val kind = entityRow[EntityTable.kind]

        val persistentData = when (kind) {
            EntityDatabaseKind.ITEM -> {
                val row = ItemsTable
                    .selectAll()
                    .where { ItemsTable.uuid eq id.toString() }
                    .firstOrNull() ?: error("Invalid database state: ItemsTable record doesn't exist")
                EntityPersistenceData.Item(
                    row[ItemsTable.prefabId].asRawEngineId(),
                    row[ItemsTable.count],
                    row[ItemsTable.maxCount],
                )
            }
            else -> null
        }

        EntityPersistentRecord(
            id,
            persistentData,
            components,
            ownerships
        )
    }
}
