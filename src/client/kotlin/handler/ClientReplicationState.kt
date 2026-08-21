package org.lain.engine.client.handler

import org.lain.engine.player.PlayerId
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.server.EntityNetworkSnapshot
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
        val authoritativeComponents: List<ComponentDto>,
    ) : SnapshotAcceptance
}

/**
 * Stores the server-owned component state separately from the live, predicted client ECS state.
 */
internal class ClientReplicationState {
    private data class EntityState(
        var revision: Long? = null,
        val components: LinkedHashMap<String, ComponentDto> = linkedMapOf(),
    )

    private val entities = mutableMapOf<PersistentId, EntityState>()
    private val predictedComponents = linkedMapOf<ReplicatedComponentKey, InteractionId>()
    private var activeEntities: Set<PersistentId> = emptySet()

    var activeInteraction: InteractionId? = null
        private set

    var processedInputTick: Long = -1
        private set

    fun clear() {
        entities.clear()
        predictedComponents.clear()
        activeInteraction = null
        activeEntities = emptySet()
        processedInputTick = -1
    }

    fun seedEntity(persistentId: PersistentId, components: List<ComponentDto>) {
        val state = entities.getOrPut(persistentId) { EntityState() }
        if (state.components.isEmpty()) {
            components.forEach { state.components[it.id] = it }
        }
    }

    fun beginInteraction(interactionId: InteractionId, entities: Set<PersistentId>) {
        activeInteraction = interactionId
        activeEntities = entities
    }

    fun endInteraction() {
        activeInteraction = null
        activeEntities = emptySet()
    }

    fun recordComponentChange(persistentId: PersistentId, componentTypeId: String) {
        val interactionId = activeInteraction ?: return
        if (persistentId !in activeEntities) return
        predictedComponents[ReplicatedComponentKey(persistentId, componentTypeId)] = interactionId
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
        entities[key.entity]?.components?.get(key.componentTypeId)

    fun authoritativeComponents(persistentId: PersistentId): List<ComponentDto> =
        entities[persistentId]?.components?.values?.toList().orEmpty()

    fun removeEntity(persistentId: PersistentId) {
        entities.remove(persistentId)
        predictedComponents.keys.removeIf { it.entity == persistentId }
    }

    fun accept(
        persistentId: PersistentId,
        snapshot: EntityNetworkSnapshot,
    ): SnapshotAcceptance {
        val state = entities.getOrPut(persistentId) { EntityState() }
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
                        state.components.values.toList(),
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
                            state.components.values.toList(),
                        )
                    }
                }
            }
        }
    }
}
