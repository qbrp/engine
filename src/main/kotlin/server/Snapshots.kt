package org.lain.engine.server

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.world.World

@Serializable
sealed class EntityNetworkSnapshot {
    @Serializable
    data class Delta(
        val baseRevision: Long?,
        val revision: Long,
        val delta: EntityDelta
    ) : EntityNetworkSnapshot()
    @Serializable
    data class Full(
        val revision: Long,
        val components: List<ReplicationSnapshot>
    ) : EntityNetworkSnapshot()
}

fun NetworkStateFrame.deltaSnapshot() = EntityNetworkSnapshot.Delta(baseRevision, revision, delta)

context(world: World)
fun EntityId.fullNetworkSnapshot() = EntityNetworkSnapshot.Full(
    networkState().revision,
    collectNetworkedComponents()
)

@Serializable
sealed interface ReplicationTarget {
    @Serializable
    data object World : ReplicationTarget

    @Serializable
    data class Entity(val persistentId: PersistentId) : ReplicationTarget
}

@Serializable
data class ReplicationFrameSnapshot(
    val world: EntityNetworkSnapshot?, //null if is empty
    val entities: Map<PersistentId, EntityNetworkSnapshot>,
    val processedInputTick: Long? = null,
) {
    fun isEmpty() = world == null && entities.isEmpty()
}

@Serializable
data class EntityDelta(
    val updated: List<ReplicationSnapshot>,
    val removed: List<String>
)

data class NetworkStateFrame(
    val baseRevision: Long?,
    val revision: Long,
    val delta: EntityDelta
)

data class WorldStateFrame(
    val worldState: NetworkStateFrame?, //null if is empty
    val entities: Map<PersistentId, NetworkStateFrame>
)

context(world: World)
fun Changes.collectUpdates(entityId: EntityId): List<Component> {
    val updates = mutableListOf<Component>()
    updated.forEachIndex { arrayId ->
        world.componentManager.getComponentArray(arrayId).componentOf(entityId)
            ?.let { component -> updates += component }
    }
    return updates
}

context(world: World)
fun Changes.collectRemoves(): List<ComponentType<*>> {
    val removes = mutableListOf<ComponentType<*>>()
    removed.forEachIndex { arrayId ->
        removes += world.componentManager.getComponentArray(arrayId).type
    }
    return removes
}

context(world: World)
fun Changes.collectChanges(entityId: EntityId): EntityDelta? {
    val updates = collectUpdates(entityId)
    val removes = collectRemoves()
    return if (updates.isNotEmpty() || removes.isNotEmpty()) {
        EntityDelta(
            updates.map { it.replicationSnapshot() },
            removes.map { it.id }
        )
    } else {
        null
    }
}

context(world: World)
fun Changes.freezeSnapshot(entity: EntityId): NetworkStateFrame? {
    val delta = collectChanges(entity) ?: return null
    val baseRevision = revision
    revision++
    return NetworkStateFrame(baseRevision, revision, delta)
}

fun World.composeStateFrame(): WorldStateFrame {
    val entities = mutableMapOf<PersistentId, NetworkStateFrame>()
    iterate<Networked, Changes, PersistentIdComponent>() { entity, _, networkState, (persistentId) ->
        if (!networkState.changed) {
            return@iterate
        }

        networkState.freezeSnapshot(entity)?.let { entities[persistentId] = it }

        networkState.clear()
    }

    val worldNetworkState = state.networkState()
    val worldEntityFrame = worldNetworkState.freezeSnapshot(state)
    worldNetworkState.clear()
    return WorldStateFrame(worldEntityFrame, entities)
}
