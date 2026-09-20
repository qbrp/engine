package org.lain.engine.server

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.has
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.data.PersistentId
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import java.util.TreeMap

data class TrackingState(
    val synced: MutableSet<PersistentId> = mutableSetOf(),
    val fresh: MutableSet<PersistentId> = mutableSetOf()
) {
    fun update(entities: Set<PersistentId>) {
        fresh.clear()
        synced.retainAll(entities)
        entities.forEach { entity ->
            if (synced.add(entity)) {
                fresh.add(entity)
            }
        }

    }
}

data class PlayerSyncState(
    val trackingPlayers: MutableMap<EntityId, LocationedPlayer> = mutableMapOf(),
    val freshPlayers: MutableSet<LocationedPlayer> = mutableSetOf(),
    val entities: TrackingState = TrackingState(),
    var isWorldSynced: Boolean = false,
    var confirmed: Boolean = false,
    val pendingInputs: TreeMap<Long, Set<InputAction>> = TreeMap(),
    var lastReceivedInputTick: Long = -1,
    var processingInputTick: Long? = null,
    var processedInputTick: Long = -1,
    var lastSentProcessedInputTick: Long = -1,
    val sentChunks: MutableMap<EngineChunkPos, EngineChunk> = mutableMapOf()
) : Component {
    fun enqueueInput(tick: Long, actions: Set<InputAction>): Boolean {
        if (tick <= lastReceivedInputTick) {
            return false
        }
        if (pendingInputs.size >= MAX_PENDING_INPUTS) {
            pendingInputs.pollLastEntry()
        }
        pendingInputs[tick] = actions.toSet()
        lastReceivedInputTick = tick
        return true
    }

    companion object {
        private const val MAX_PENDING_INPUTS = 256
    }
}

fun World.tickQueuedPlayerInputsSystem() {
    iterate<PlayerInput, PlayerSyncState> { _, input, syncState ->
        val pendingInput = syncState.pendingInputs.pollFirstEntry() ?: return@iterate
        input.actions.clear()
        input.actions.addAll(pendingInput.value)
        input.tick = pendingInput.key
        syncState.processingInputTick = pendingInput.key
    }
}

fun World.confirmProcessedPlayerInputsSystem() {
    iterate<PlayerSyncState> { _, syncState ->
        val processed = syncState.processingInputTick ?: return@iterate
        syncState.processedInputTick = maxOf(syncState.processedInputTick, processed)
        syncState.processingInputTick = null
    }
}

fun World.tickPlayerTrackingSystem(server: EngineServer, desynchronizationRadius: Int) {
    val squaredDesynchronizationRadius = desynchronizationRadius * desynchronizationRadius
    iterate<Location, Interests, PlayerSyncState>()
        { player, (position), interests, syncState ->
            val trackingPlayers = syncState.trackingPlayers
            val freshPlayers = syncState.freshPlayers
            val entities = syncState.entities
            if ((server.isReplay && !player.hasComponent<ReplayViewer>()) || player.hasComponent<PlayerInstantiationConfirmation>()) {
                syncState.confirmed = false
                return@iterate
            }
            syncState.confirmed = true

            freshPlayers.clear()

            val playersOutRadius = trackingPlayers.values.toMutableList()
            val frame = interests.frame
            frame.interestPlayers.forEach { locationedInterestPlayer ->
                val (interestPlayer, _) = locationedInterestPlayer
                playersOutRadius -= locationedInterestPlayer
                trackingPlayers.getOrPut(interestPlayer.entity) {
                    freshPlayers += locationedInterestPlayer
                    locationedInterestPlayer
                }
            }
            playersOutRadius.forEach { (player, outRadiusLocation) ->
                if (player.destroyed || outRadiusLocation.position.squaredDistanceTo(position) > squaredDesynchronizationRadius) {
                    trackingPlayers.remove(player.entity)
                }
            }

            val availableInterestsVoxels = frame.interestVoxels
                .filter { syncState.sentChunks.contains(it.chunkPos) }
                .map { it.id }

            entities.update(frame.interestEntities + availableInterestsVoxels)
        }
}
