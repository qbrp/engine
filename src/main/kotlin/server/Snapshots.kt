package org.lain.engine.server

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.engine.storage.ComponentDto
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.toSnapshotDto
import org.lain.engine.world.World
import kotlin.collections.plusAssign
import kotlin.collections.set

@Serializable
sealed class EntityNetworkSnapshot {
    @Serializable
    data class Delta(
        val baseRevision: Long?,
        val revision: Long,
        val delta: EntityDelta
    ) : EntityNetworkSnapshot()
    data class Full(
        val revision: Long,
        val components: List<ComponentDto>
    ) : EntityNetworkSnapshot()
}

@Serializable
data class EntityDelta(
    val updated: List<ComponentDto>,
    val removed: List<String>
)

data class EntityStateFrame(
    val entity: EntityId,
    val persistentId: PersistentId?,
    val baseRevision: Long?,
    val revision: Long,
    val delta: EntityDelta
)

data class WorldStateFrame(
    val serverTick: Long,
    val worldState: EntityStateFrame?, //null if is empty
    val entities: Map<PersistentId, EntityStateFrame>
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
            updates.map { it.toSnapshotDto() },
            removes.map { it.id }
        )
    } else {
        null
    }
}

context(world: World)
fun Changes.freezeSnapshot(persistentId: PersistentId?, entity: EntityId): EntityStateFrame? {
    val entityDelta = collectChanges(entity)
    return if (entityDelta != null) {
        val baseRevision = revision
        val newRevision = baseRevision + 1
        EntityStateFrame(
            entity,
            persistentId,
            baseRevision,
            newRevision,
            entityDelta
        ).also {
            revision = newRevision
        }
    } else {
        null
    }
}

fun World.composeStateFrame(): WorldStateFrame {
    val entities = mutableMapOf<PersistentId, EntityStateFrame>()
    iterate<Networked, Changes, PersistentIdComponent>() { entity, _, networkState, (persistentId) ->
        if (!networkState.changed) {
            return@iterate
        }

        val snapshot = networkState.freezeSnapshot(persistentId, entity)
        if (snapshot != null) {
            entities[persistentId] = snapshot
        }

        networkState.clear()
    }

    val worldNetworkState = state.networkState()
    val worldEntityFrame = worldNetworkState.freezeSnapshot(null, state)
    worldNetworkState.clear()
    return WorldStateFrame(ticks, worldEntityFrame, entities)
}