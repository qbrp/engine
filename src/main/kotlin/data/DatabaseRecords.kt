package org.lain.engine.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptValue
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
    val payload: ComponentPayload,
    val error: String?
)

@JvmInline
@Serializable
value class ComponentByteArray(val array: ByteArray)

@Serializable
sealed interface ComponentPayload {
    @Serializable
    @SerialName("kotlin")
    data class Kotlin(
        val cbor: ComponentByteArray,
    ) : ComponentPayload

    @Serializable
    sealed interface Script : ComponentPayload {
        @Serializable
        @SerialName("script_json")
        data class Json(
            val value: ScriptValue,
        ) : Script

        @Serializable
        @SerialName("script_cbor")
        data class Cbor(
            val cbor: ComponentByteArray,
        ) : Script
    }
}

fun ComponentPersistentRecord.decode(): ComponentSnapshot {
    return payload.decode(id)
}
