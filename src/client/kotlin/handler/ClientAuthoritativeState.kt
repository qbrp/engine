package org.lain.engine.client.handler

import org.lain.engine.client.handler.SnapshotAcceptance.*
import org.lain.engine.data.PersistentId
import org.lain.engine.server.protocolError
import org.lain.engine.server.replication.EntityStateUpdate
import org.lain.engine.server.replication.ReplicationSnapshot
import org.lain.engine.server.replication.ReplicationTarget

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

internal class ClientAuthoritativeState {
    private data class TargetState(
        var revision: Long,
        val components: MutableMap<String, ReplicationSnapshot> = mutableMapOf(),
    )

    private val targets = mutableMapOf<ReplicationTarget, TargetState>()

    fun clear() {
        targets.clear()
    }

    fun seed(target: ReplicationTarget, snapshot: EntityStateUpdate.Full) {
        targets[target] = TargetState(
            snapshot.revision,
            snapshot.components.associateByTo(mutableMapOf()) { it.id },
        )
    }

    fun removeEntity(persistentId: PersistentId) {
        targets.remove(ReplicationTarget.Entity(persistentId))
    }

    fun component(persistentId: PersistentId, componentTypeId: String): ReplicationSnapshot? =
        targets[ReplicationTarget.Entity(persistentId)]?.components?.get(componentTypeId)

    fun accept(
        target: ReplicationTarget,
        snapshot: EntityStateUpdate,
    ): SnapshotAcceptance {
        return when (snapshot) {
            is EntityStateUpdate.Full -> {
                val state = targets.getOrPut(target) { TargetState(snapshot.revision) }
                val currentRevision = state.revision
                if (snapshot.revision < currentRevision) {
                    Ignored
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
                val state = targets[target]
                    ?: protocolError("Получен delta-снимок не полностью синхронизированной сущности")
                val currentRevision = state.revision
                when {
                    snapshot.delta.revision <= currentRevision ->
                        Ignored

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
