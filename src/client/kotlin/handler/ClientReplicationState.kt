package org.lain.engine.client.handler

import org.lain.engine.client.handler.SnapshotAcceptance.*
import org.lain.engine.player.PlayerId
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.server.replication.EntityStateUpdate
import org.lain.engine.server.replication.ReplicationTarget
import org.lain.engine.data.PersistentId
import org.lain.engine.server.replication.ReplicationSnapshot
import org.lain.engine.server.protocolError

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
        val updated: List<ReplicationSnapshot>,
        val removed: Set<String>,
    ) : SnapshotAcceptance
}

internal class ClientReplicationState {
    private data class TargetState(
        var revision: Long,
        val components: MutableMap<String, ReplicationSnapshot> = mutableMapOf(),
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

    fun seed(target: ReplicationTarget, snapshot: EntityStateUpdate.Full) {
        targets[target] = TargetState(
            snapshot.revision,
            snapshot.components.associateByTo(mutableMapOf()) { it.id }
        )
    }

    fun beginInteraction(interactionId: InteractionId, entities: Set<PersistentId>) {
        activePrediction = ActivePrediction(interactionId, entities)
    }

    fun endInteraction() {
        activePrediction = null
    }

    fun onComponentChange(entityId: PersistentId, componentTypeId: String) {
        val prediction = activePrediction ?: return
        if (entityId !in prediction.entities) return
        predictedComponents[ReplicatedComponentKey(entityId, componentTypeId)] =
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

    fun authoritativeComponent(key: ReplicatedComponentKey): ReplicationSnapshot? =
        targets[ReplicationTarget.Entity(key.entity)]?.components?.get(key.componentTypeId)

    fun removeEntity(persistentId: PersistentId) {
        targets.remove(ReplicationTarget.Entity(persistentId))
        predictedComponents.keys.removeIf { it.entity == persistentId }
    }

    fun accept(
        target: ReplicationTarget,
        snapshot: EntityStateUpdate,
    ): SnapshotAcceptance {
        return when (snapshot) {
            is EntityStateUpdate.Full -> {
                val state = targets.getOrPut(target) { TargetState(snapshot.revision) }
                val currentRevision = state.revision
                if (snapshot.revision < currentRevision) {
                    SnapshotAcceptance.Ignored
                } else {
                    val newComponents = snapshot.components.associateBy { it.id }
                    val removed = state.components.keys - newComponents.keys
                    state.components.clear()
                    state.components.putAll(newComponents)
                    state.revision = snapshot.revision
                    Accepted(
                        snapshot.revision,
                        snapshot.components,
                        removed,
                    )
                }
            }

            is EntityStateUpdate.Delta -> {
                val state = targets[target] ?: protocolError("Получен delta-снимок не полностью синхронизированной сущности")
                val currentRevision = state.revision
                when {
                    snapshot.delta.revision <= currentRevision ->
                        SnapshotAcceptance.Ignored

                    snapshot.delta.baseRevision != currentRevision ->
                        Gap(
                            currentRevision,
                            snapshot.delta.baseRevision,
                            snapshot.delta.revision,
                        )

                    else -> {
                        snapshot.delta.updated.forEach { state.components[it.id] = it }
                        snapshot.delta.removed.forEach(state.components::remove)
                        state.revision = snapshot.delta.revision
                        Accepted(
                            snapshot.delta.revision,
                            snapshot.delta.updated,
                            snapshot.delta.removed.toSet(),
                        )
                    }
                }
            }
        }
    }
}
