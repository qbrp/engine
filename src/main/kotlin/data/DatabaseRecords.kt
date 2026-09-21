package org.lain.engine.data

import kotlinx.serialization.Serializable
import org.lain.engine.script.EngineId
import org.lain.engine.util.ecs.EntityRelation
import org.lain.engine.util.ecs.RelationTypeId

@JvmInline
@Serializable
value class RawEngineId(val id: String) {
    fun parse() = EngineId(id)
}

val EngineId.stringRepresentation: RawEngineId
    get() = full.asRawEngineId()

fun String.asRawEngineId() = RawEngineId(this)

enum class EntityDatabaseKind {
    ITEM, PLAYER, CHARACTER, VOXEL
}

sealed interface EntityPersistenceData {
    data class Item(
        val prefabId: RawEngineId,
        val count: Int,
        val maxCount: Int,
    ) : EntityPersistenceData
    data class Character(
        val look: String,
        val items: SerializedInventory
    ) : EntityPersistenceData
}

data class RelationPersistentRecord(
    override val id: RelationTypeId,
    override val child: PersistentId,
) : EntityRelation

data class EntityPersistentRecord(
    val uuid: PersistentId,
    val data: EntityPersistenceData?,
    val components: List<ComponentPersistentRecord>,
    val relations: List<RelationPersistentRecord>,
)

@Serializable
data class ComponentPersistentRecord(
    val id: RawEngineId,
    val version: Int,
    val payload: ByteArray,
    val error: String?
)

fun ComponentPersistentRecord.decode(): ComponentSnapshot {
    return PersistentComponentDto.decode(payload).decode()
}
