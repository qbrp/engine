package org.lain.engine.client.handler

import org.lain.engine.player.PlayerId
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.server.EntityNetworkSnapshot
import org.lain.engine.server.ReplicationTarget
import org.lain.engine.storage.ComponentDto
import org.lain.engine.storage.PersistentId

internal data class ReplicatedComponentKey(
    val entity: PersistentId,
    val componentTypeId: String,
)

internal sealed interface SnapshotAcceptance {
    data object Ignored : SnapshotAcceptance

    data class Gap(
        val currentRevision: Long?,
        val baseRevision: Long?,
        val revision: Long,
    ) : SnapshotAcceptance

    data class Accepted(
        val revision: Long,
        val updated: List<ComponentDto>,
        val removed: Set<String>,
    ) : SnapshotAcceptance
}

/**
 * Stores the server-owned component state separately from the live, predicted client ECS state.
 */
internal class ClientReplicationState {
    private data class TargetState(
        var revision: Long? = null,
        val components: LinkedHashMap<String, ComponentDto> = linkedMapOf(),
    )

    private data class ActivePrediction(
        val interactionId: InteractionId,
        val entities: Set<PersistentId>,
    )

    private val targets = mutableMapOf<ReplicationTarget, TargetState>()
    private val predictedComponents = linkedMapOf<ReplicatedComponentKey, InteractionId>()
    private var activePrediction: ActivePrediction? = null
    private var processedInputTick: Long = -1

    fun clear() {
        targets.clear()
        predictedComponents.clear()
        activePrediction = null
        processedInputTick = -1
    }

    fun seed(target: ReplicationTarget, components: List<ComponentDto>) {
        val state = targets.getOrPut(target) { TargetState() }
        if (state.components.isEmpty()) {
            components.forEach { state.components[it.id] = it }
        }
    }

    fun beginInteraction(interactionId: InteractionId, entities: Set<PersistentId>) {
        activePrediction = ActivePrediction(interactionId, entities)
    }

    fun endInteraction() {
        activePrediction = null
    }

    fun recordComponentChange(persistentId: PersistentId, componentTypeId: String) {
        val prediction = activePrediction ?: return
        if (persistentId !in prediction.entities) return
        predictedComponents[ReplicatedComponentKey(persistentId, componentTypeId)] =
            prediction.interactionId
    }

    fun isPredicted(persistentId: PersistentId, componentTypeId: String): Boolean =
        ReplicatedComponentKey(persistentId, componentTypeId) in predictedComponents

    fun confirmInput(playerId: PlayerId, inputTick: Long): List<ReplicatedComponentKey> {
        if (inputTick <= processedInputTick) return emptyList()
        processedInputTick = inputTick

        val confirmed = predictedComponents
            .filterValues { interaction ->
                interaction.source == playerId && interaction.inputTick <= inputTick
            }
            .keys
            .toList()
        confirmed.forEach(predictedComponents::remove)
        return confirmed
    }

    fun authoritativeComponent(key: ReplicatedComponentKey): ComponentDto? =
        targets[ReplicationTarget.Entity(key.entity)]?.components?.get(key.componentTypeId)

    fun removeEntity(persistentId: PersistentId) {
        targets.remove(ReplicationTarget.Entity(persistentId))
        predictedComponents.keys.removeIf { it.entity == persistentId }
    }

    fun accept(
        target: ReplicationTarget,
        snapshot: EntityNetworkSnapshot,
    ): SnapshotAcceptance {
        val state = targets.getOrPut(target) { TargetState() }
        return when (snapshot) {
            is EntityNetworkSnapshot.Full -> {
                val currentRevision = state.revision
                if (currentRevision != null && snapshot.revision < currentRevision) {
                    SnapshotAcceptance.Ignored
                } else {
                    val newComponents = snapshot.components.associateByTo(linkedMapOf()) { it.id }
                    val removed = state.components.keys - newComponents.keys
                    state.components.clear()
                    state.components.putAll(newComponents)
                    state.revision = snapshot.revision
                    SnapshotAcceptance.Accepted(
                        snapshot.revision,
                        snapshot.components,
                        removed,
                    )
                }
            }

            is EntityNetworkSnapshot.Delta -> {
                val currentRevision = state.revision
                when {
                    currentRevision != null && snapshot.revision <= currentRevision ->
                        SnapshotAcceptance.Ignored

                    snapshot.baseRevision != currentRevision ->
                        SnapshotAcceptance.Gap(
                            currentRevision,
                            snapshot.baseRevision,
                            snapshot.revision,
                        )

                    else -> {
                        snapshot.delta.updated.forEach { state.components[it.id] = it }
                        snapshot.delta.removed.forEach(state.components::remove)
                        state.revision = snapshot.revision
                        SnapshotAcceptance.Accepted(
                            snapshot.revision,
                            snapshot.delta.updated,
                            snapshot.delta.removed.toSet(),
                        )
                    }
                }
            }
        }
    }
}
