package org.lain.engine.server.replication

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.PersistentId
import org.lain.engine.player.PlayerComponent
import org.lain.engine.server.ServerHandler
import org.lain.engine.world.World

@Serializable
sealed interface ReplicationTarget {
    @Serializable
    data object World : ReplicationTarget

    @Serializable
    data class Entity(val persistentId: PersistentId) : ReplicationTarget
}

@Serializable
data class ReplicationFrame(
    val world: EntityReplicationUpdate?, //null if is empty
    val entities: Map<PersistentId, EntityReplicationUpdate>,
    val processedInputTick: Long? = null,
) {
    fun isEmpty() = world == null && entities.isEmpty()
}

fun World.sendReplicationPackets(
    replicationDeltaSnapshot: ReplicationDeltaSnapshot,
    handler: ServerHandler
) {
    val world = this
    val entitiesFrame = replicationDeltaSnapshot.entities
    val fullSnapshotCache = mutableMapOf<PersistentId, EntityReplicationUpdate.Full>()

    iterate<PlayerComponent, PlayerSyncState>() { _, (player), state ->
        if (!state.confirmed) return@iterate
        val entities = mutableMapOf<PersistentId, EntityReplicationUpdate>()

        state.freshPlayers.forEach { (playerToSync) ->
            handler.sendFullPlayerState(player, playerToSync)
        }
        state.entities.let { trackState ->
            trackState.fresh.forEach {
                val entity = persistentIdToEntity[it] ?: return@forEach
                val state = fullSnapshotCache.getOrPut(it) { entity.fullReplicationUpdate() }
                entities[it] = state
            }
            (trackState.synced - trackState.fresh).forEach {
                val snapshot = entitiesFrame[it] ?: return@forEach
                entities[it] = snapshot.toReplicationUpdate()
            }
        }

        val worldSnapshot = if (!state.isWorldSynced) {
            state.isWorldSynced = true
            world.state.fullReplicationUpdate()
        } else {
            replicationDeltaSnapshot.worldState?.toReplicationUpdate()
        }

        val processedInputTick = if (state.processedInputTick > state.lastSentProcessedInputTick) {
            state.lastSentProcessedInputTick = state.processedInputTick
            state.processedInputTick
        } else {
            null
        }

        handler.sendReplicationFrame(
            player,
            ReplicationFrame(worldSnapshot, entities, processedInputTick),
        )
    }
}