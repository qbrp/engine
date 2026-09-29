package org.lain.engine.server.replication

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.LOGGER
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.ScriptComponentFreezeException
import org.lain.engine.server.replication.replicationSnapshot
import org.lain.engine.util.getDebugName
import org.lain.engine.util.getEntityDebugNameId
import org.lain.engine.world.World

@Serializable
sealed class EntityStateUpdate {
    @Serializable
    data class Delta(val delta: EntityDelta) : EntityStateUpdate()

    @Serializable
    data class Full(
        val revision: Long,
        val components: List<ReplicationSnapshot>
    ) : EntityStateUpdate()
}

fun EntityDelta.toReplicationUpdate() = EntityStateUpdate.Delta(this)

context(world: World)
fun EntityId.fullReplicationUpdate() = EntityStateUpdate.Full(
    networkState().revision,
    captureSnapshotsCatching(
        world.componentManager.getNetworkedComponents(this),
        this
    )
)

@Serializable
data class EntityDelta(
    val baseRevision: Long?,
    val revision: Long,
    val updated: List<ReplicationSnapshot>,
    val removed: List<String>
)

data class ReplicationDeltaSnapshot(
    val worldState: EntityDelta?, //null if is empty
    val entities: Map<PersistentId, EntityDelta>
)

context(world: World)
private fun captureSnapshotsCatching(components: List<Component>, entityId: EntityId): List<ReplicationSnapshot> {
    return components.mapNotNull {
        try {
            it.replicationSnapshot()
        } catch (e: ScriptComponentFreezeException) {
            LOGGER.error("Не удалось создать снимок скриптового компонента ${e.component} при репликации сущности ${entityId.getEntityDebugNameId()}", e)
            null
        }
    }
}

context(world: World)
fun Changes.captureDelta(entity: EntityId): EntityDelta? {
    val updates = collectUpdates(entity)
    val removes = collectRemoves()
    if (updates.isEmpty() && removes.isEmpty()) {
        return null
    }
    val baseRevision = revision

    val updated = captureSnapshotsCatching(updates, entity)

    revision++
    return EntityDelta(
        baseRevision,
        revision,
        updated,
        removes.map { it.id }
    )
}

fun World.captureReplicationDelta(): ReplicationDeltaSnapshot {
    val entities = mutableMapOf<PersistentId, EntityDelta>()
    iterate<Networked, Changes, PersistentIdComponent>() { entity, _, networkState, (persistentId) ->
        if (!networkState.changed) {
            return@iterate
        }

        networkState.captureDelta(entity)?.let { entities[persistentId] = it }
        networkState.clear()
    }

    val worldNetworkState = state.networkState()
    val worldEntityFrame = worldNetworkState.captureDelta(state)
    worldNetworkState.clear()
    return ReplicationDeltaSnapshot(worldEntityFrame, entities)
}
