package org.lain.engine.server.replication

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.data.PersistentId
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ReplayViewer
import org.lain.engine.util.replaceWith
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.location
import java.util.TreeMap

data class TrackingState(
    val previousTickResident: MutableSet<PersistentId> = mutableSetOf(),
    val resident: MutableSet<PersistentId> = mutableSetOf(),
    val synced: MutableSet<PersistentId> = mutableSetOf(),
    val fresh: MutableSet<PersistentId> = mutableSetOf()
) {
    fun update(
        activeEntities: Set<PersistentId>, // активные сущности
        residentEntities: Set<PersistentId> = activeEntities, // активные + удерживаемые
    ) {
        require(residentEntities.containsAll(activeEntities)) {
            "Active replication entities must also be resident"
        }

        previousTickResident.clear()
        previousTickResident.addAll(resident)

        fresh.replaceWith(activeEntities - synced)
        synced.replaceWith(activeEntities)

        resident.clear()
        resident.addAll(residentEntities)
    }
}

context(world: World)
private fun EnginePlayer.replicationPersistentIds(): Set<PersistentId> =
    collectReplicationEntities().mapNotNullTo(mutableSetOf()) { entity ->
        entity.getComponent<PersistentIdComponent>()?.id
    }

data class QueuedPlayerInput(
    val actions: Set<InputAction>,
    var lastTick: Long,
)

data class PlayerReplicationState(
    val trackedPlayers: MutableSet<EnginePlayer> = mutableSetOf(),
    val newlyTrackedPlayers: MutableSet<EnginePlayer> = mutableSetOf(),
    val entities: TrackingState = TrackingState(),
    var isWorldSynced: Boolean = false,
    var confirmed: Boolean = false,
    val pendingInputs: TreeMap<Long, QueuedPlayerInput> = TreeMap(),
    var lastReceivedInputTick: Long = -1,
    var processingInputTick: Long? = null,
    var processedInputTick: Long = -1,
    var lastSentProcessedInputTick: Long = -1,
    val sentChunks: MutableMap<EngineChunkPos, EngineChunk> = mutableMapOf(),
    val requestedResyncs: MutableSet<ReplicationTarget> = mutableSetOf(),
) : Component {
    fun enqueueInput(tick: Long, actions: Set<InputAction>): Boolean {
        if (tick <= lastReceivedInputTick) {
            return false
        }

        val copiedActions = actions.toSet()
        val lastInput = pendingInputs.lastEntry()?.value
        if (lastInput?.actions == copiedActions) {
            lastInput.lastTick = tick
        } else {
            if (pendingInputs.size >= MAX_PENDING_INPUTS) {
                pendingInputs.pollLastEntry()
            }
            pendingInputs[tick] = QueuedPlayerInput(copiedActions, tick)
        }
        lastReceivedInputTick = tick
        return true
    }

    companion object {
        private const val MAX_PENDING_INPUTS = 256
    }
}

fun World.tickQueuedPlayerInputsSystem() {
    iterate<PlayerInput, PlayerReplicationState> { _, input, syncState ->
        val pendingInput = syncState.pendingInputs.pollFirstEntry() ?: return@iterate
        input.actions.clear()
        input.actions.addAll(pendingInput.value.actions)
        input.tick = pendingInput.key
        syncState.processingInputTick = pendingInput.value.lastTick
    }
}

fun World.confirmProcessedPlayerInputsSystem() {
    iterate<PlayerReplicationState> { _, syncState ->
        val processed = syncState.processingInputTick ?: return@iterate
        syncState.processedInputTick = maxOf(syncState.processedInputTick, processed)
        syncState.processingInputTick = null
    }
}

fun World.tickPlayerTrackingSystem(server: EngineServer, desynchronizationRadius: Int) {
    val squaredDesynchronizationRadius = desynchronizationRadius * desynchronizationRadius
    val replicationEntitiesByPlayer =
        players.asSequence().filterNot { it.destroyed }.associate { it.entity to it.replicationPersistentIds() }
    val onlinePlayerReplicationEntities = replicationEntitiesByPlayer.values.flatten().toSet()

    iterate<Location, Interests, PlayerReplicationState>() { player, (position), interests, syncState ->
        // состояние на момент прошлого тика - кто считался отслеживаемым и свежим
        val trackedPlayers = syncState.trackedPlayers
        val freshPlayers = syncState.newlyTrackedPlayers

        val entities = syncState.entities
        if ((server.isReplay && !player.hasComponent<ReplayViewer>()) || player.hasComponent<PlayerInstantiationConfirmation>()) {
            syncState.confirmed = false
            return@iterate
        }
        syncState.confirmed = true

        val frame = interests.frame

        // считаем свежих игроков - тех, кого нет в trackingPlayers
        freshPlayers.replaceWith(frame.interestPlayers - trackedPlayers)

        val trackedPlayersOutsideInterest = (trackedPlayers - frame.interestPlayers)
            .filterTo(mutableSetOf()) { player ->
                player.destroyed || player.location.position.squaredDistanceTo(position) > squaredDesynchronizationRadius
            }

        trackedPlayers.addAll(freshPlayers)
        trackedPlayers.removeAll(trackedPlayersOutsideInterest)

        val availableInterestsVoxels =
            frame.interestVoxels.filter { syncState.sentChunks.contains(it.chunkPos) }.map { it.id }

        // подхватываем зависимые сущности у игроков
        val activePlayers = trackedPlayers.map { it.entity } + player
        val activePlayerReplicationEntities = activePlayers.flatMapTo(mutableSetOf()) {
            replicationEntitiesByPlayer[it].orEmpty()
        }

        val activeEntities = frame.interestEntities + availableInterestsVoxels + activePlayerReplicationEntities

        entities.update(
            activeEntities = activeEntities,
            residentEntities = activeEntities + onlinePlayerReplicationEntities,
        )
    }
}
