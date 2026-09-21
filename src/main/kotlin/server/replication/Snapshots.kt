package org.lain.engine.server.replication

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.world.World

@Serializable
sealed class EntityReplicationUpdate {
    @Serializable
    data class Delta(val delta: EntityDelta) : EntityReplicationUpdate()

    @Serializable
    data class Full(
        val revision: Long,
        val components: List<ReplicationSnapshot>
    ) : EntityReplicationUpdate()
}

fun EntityDelta.toReplicationUpdate() = EntityReplicationUpdate.Delta(this)

context(world: World)
fun EntityId.fullReplicationUpdate() = EntityReplicationUpdate.Full(
    networkState().revision,
    collectNetworkedComponents()
)

context(world: World)
fun EntityId.collectNetworkedComponents() = world.componentManager.getNetworkedComponents(this)
    .map { component -> component.replicationSnapshot() }

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
fun Changes.captureDelta(entity: EntityId): EntityDelta? {
    val updates = collectUpdates(entity)
    val removes = collectRemoves()
    if (updates.isEmpty() && removes.isEmpty()) {
        return null
    }
    val baseRevision = revision
    revision++
    return EntityDelta(
        baseRevision,
        revision,
        updates.map { it.replicationSnapshot() },
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
