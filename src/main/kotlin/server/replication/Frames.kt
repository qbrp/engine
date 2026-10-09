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
    val world: EntityStateUpdate?, //null if is empty
    val entities: Map<PersistentId, EntityStateUpdate> = emptyMap(),
    val out: Set<PersistentId> = emptySet(),
    val processedInputTick: Long? = null,
) {
    fun isEmpty() =
        world == null && entities.isEmpty() && out.isEmpty()
}

fun World.sendReplicationPackets(
    replicationDeltaSnapshot: ReplicationDeltaSnapshot,
    handler: ServerHandler
) {
    val world = this
    val entitiesFrame = replicationDeltaSnapshot.entities
    val fullSnapshotCache = mutableMapOf<PersistentId, EntityStateUpdate.Full>()

    iterate<PlayerComponent, PlayerReplicationState>() { _, (player), state ->
        if (!state.confirmed) return@iterate
        val trackState = state.entities
        val entities = mutableMapOf<PersistentId, EntityStateUpdate>()
        val out = trackState.previousTickResident - trackState.resident

        state.freshPlayers.forEach { (playerToSync) ->
            handler.sendFullPlayerState(player, playerToSync)
        }

        val requestedEntityResyncs = state.requestedResyncs
            .filterIsInstance<ReplicationTarget.Entity>()
            .mapTo(mutableSetOf()) { it.persistentId }
        val resyncEntities = requestedEntityResyncs.filterTo(mutableSetOf()) { it in trackState.synced }
        val fullEntities = trackState.fresh + resyncEntities
        fullEntities.forEach {
            val entity = persistentIdToEntity[it] ?: return@forEach
            val state = fullSnapshotCache.getOrPut(it) { entity.fullReplicationUpdate() }
            entities[it] = state
        }
        (trackState.synced - fullEntities).forEach {
            val snapshot = entitiesFrame[it] ?: return@forEach
            entities[it] = snapshot.toReplicationUpdate()
        }

        val worldResyncRequested = ReplicationTarget.World in state.requestedResyncs
        val sendsFullWorld = !state.isWorldSynced || worldResyncRequested
        val worldSnapshot = if (sendsFullWorld) {
            world.state.fullReplicationUpdate()
        } else {
            replicationDeltaSnapshot.worldState?.toReplicationUpdate()
        }

        val acknowledgedInputTick =
            state.processedInputTick.takeIf { it > state.lastSentProcessedInputTick }

        val frame = ReplicationFrame(
            world = worldSnapshot,
            entities = entities,
            out = out,
            processedInputTick = acknowledgedInputTick,
        )

        handler.sendReplicationFrame(player, frame)

        if (sendsFullWorld) {
            state.isWorldSynced = true
        }
        if (worldResyncRequested) {
            state.requestedResyncs -= ReplicationTarget.World
        }
        resyncEntities.forEach { persistentId ->
            state.requestedResyncs -= ReplicationTarget.Entity(persistentId)
        }
        requestedEntityResyncs
            .filterNotTo(mutableSetOf()) { it in trackState.resident }
            .forEach { persistentId ->
                state.requestedResyncs -= ReplicationTarget.Entity(persistentId)
            }
        if (acknowledgedInputTick != null) {
            state.lastSentProcessedInputTick = acknowledgedInputTick
        }
    }
}
