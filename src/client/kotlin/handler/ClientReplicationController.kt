package org.lain.engine.client.handler

import kotlinx.coroutines.CoroutineScope
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.GameSession
import org.lain.engine.data.EntityResolver
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.server.EntityNetworkSnapshot
import org.lain.engine.server.ReplicationFrameSnapshot
import org.lain.engine.server.ReplicationTarget
import org.lain.engine.server.protocolError
import org.lain.engine.server.networkState
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.VoxelPosId
import org.lain.engine.player.collectReplicationEntities
import org.lain.engine.server.Networked
import org.lain.engine.server.ReplicationSnapshot
import org.lain.engine.transport.packet.InitialReplicationState
import org.lain.engine.util.Log
import org.lain.engine.util.LogLevel
import org.lain.engine.util.LogMessages
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.getEntityDebugNameId
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import kotlin.collections.set

class ClientReplicationController(
    private val gameSession: GameSession,
    initialReplicationState: InitialReplicationState,
    private val requestResync: (ReplicationTarget) -> Unit,
) {
    private val replicationWorld: World = gameSession.world
    private val replicationState = ClientReplicationState()
    private val targetsAwaitingResync = mutableSetOf<ReplicationTarget>()

    init {
        with(replicationWorld) {
            componentManager.networkedComponentChangeListener = listener@{ entity, type ->
                val persistentId =
                    entity.getComponent<PersistentIdComponent>()?.id ?: return@listener
                replicationState.onComponentChange(persistentId, type.id)
            }
            componentManager.entityDestroyedListener = { _, persistentId ->
                persistentId?.let(::removeEntity)
            }

            replicationState.seed(
                ReplicationTarget.World,
                initialReplicationState.world
            )

            initialReplicationState.snapshots.forEach { (persistentId, snapshot) ->
                replicationState.seed(ReplicationTarget.Entity(persistentId), snapshot)
            }
        }
    }

    fun beginPrediction(playerEntity: EntityId, interactionId: InteractionId) {
        replicationState.beginInteraction(
            interactionId,
            replicationWorld.collectPredictionEntities(),
        )
    }

    fun endPrediction() {
        replicationState.endInteraction()
    }

    fun apply(frame: ReplicationFrameSnapshot) {
        try {
            applyFrame(frame)
        } catch (exception: Exception) {
            LOGGER.error("Не удалось применить кадр репликации", exception)
            gameSession.client.infrastructure.disconnect(
                exception.message ?: "Не удалось применить кадр репликации",
            )
        }
    }

    fun removeEntity(persistentId: PersistentId) {
        replicationState.removeEntity(persistentId)
        targetsAwaitingResync.remove(ReplicationTarget.Entity(persistentId))
    }

    fun close() {
        replicationWorld.componentManager.networkedComponentChangeListener = null
        replicationWorld.componentManager.entityDestroyedListener = null
        replicationState.clear()
        targetsAwaitingResync.clear()
    }

    private fun ensureEntity(persistentId: PersistentId): EntityId {
        replicationWorld.persistentIdToEntity[persistentId]?.let { return it }

        return replicationWorld.addEntity().also { entity ->
            with(replicationWorld) {
                entity.setComponent(PersistentIdComponent(persistentId))
                entity.setComponent(Networked)
            }

            if (persistentId is VoxelPosId) {
                val chunkPos = EngineChunkPos(persistentId.pos)
                val chunk = replicationWorld.chunkStorage.getChunk(chunkPos)
                    ?: protocolError("Получена сущность $persistentId на непрогруженном $chunkPos")
                chunk.dynamicVoxels[persistentId.pos] = entity
            }
        }
    }

    private fun applyFrame(frame: ReplicationFrameSnapshot) {
        frame.world?.let { applyWorldState(it) }

        val acceptedSnapshots = frame.entities.mapNotNull { (id, snapshot) ->
            acceptEntitySnapshot(id, snapshot)
        }

        acceptedSnapshots.forEach { ensureEntity(it.persistentId) }
        acceptedSnapshots.forEach(::applyEntitySnapshot)

        frame.processedInputTick?.let { processedInputTick ->
            applyProcessedInput(processedInputTick)
        }
    }

    private fun acceptEntitySnapshot(
        persistentId: PersistentId,
        snapshot: EntityNetworkSnapshot,
    ): AcceptedEntitySnapshot? {
        if (snapshot is EntityNetworkSnapshot.Delta && replicationWorld.persistentIdToEntity[persistentId] == null) {
            protocolError("Получен частичный снапшот отсутствующей сущности $persistentId")
        }

        val accepted = acceptSnapshot(ReplicationTarget.Entity(persistentId), snapshot)
            ?: return null

        return AcceptedEntitySnapshot(persistentId, accepted)
    }

    private fun acceptSnapshot(
        target: ReplicationTarget,
        snapshot: EntityNetworkSnapshot,
    ): SnapshotAcceptance.Accepted? {
        val accepted = when (val acceptance = replicationState.accept(target, snapshot)) {
            SnapshotAcceptance.Ignored -> return null
            is SnapshotAcceptance.Gap -> {
                val targetName = when (target) {
                    ReplicationTarget.World -> "World state"
                    is ReplicationTarget.Entity -> "Entity ${target.persistentId}"
                }
                LOGGER.warn(
                    "$targetName revision gap: local=${acceptance.currentRevision}, " +
                        "incoming=${acceptance.baseRevision}->${acceptance.revision}",
                )
                if (targetsAwaitingResync.add(target)) {
                    requestResync(target)
                }
                return null
            }

            is SnapshotAcceptance.Accepted -> acceptance
        }

        if (snapshot is EntityNetworkSnapshot.Full) {
            targetsAwaitingResync.remove(target)
        }

        return accepted
    }

    private fun applyEntitySnapshot(acceptedSnapshot: AcceptedEntitySnapshot) = with(replicationWorld) {
        val persistentId = acceptedSnapshot.persistentId
        val snapshot = acceptedSnapshot.snapshot
        val updated = snapshot.updated.filterNot { component ->
            replicationState.isPredicted(persistentId, component.id)
        }
        val removed = snapshot.removed.filterNot { componentTypeId ->
            replicationState.isPredicted(persistentId, componentTypeId)
        }
        val entity = applyEntityComponents(
            persistentId,
            updated,
            removed,
        )
        entity.networkState().revision = snapshot.revision

        gameSession.logInMainThread {
            Log(
                LogMessages.ENTITY_SYNC_ADD,
                LogLevel.INFO,
                data = mapOf(
                    "entity" to entity.getEntityDebugNameId().name,
                    "persistent_id" to persistentId.toString(),
                    "components" to updated.joinToString(),
                ),
                world = world.id,
                tick = it,
            )
        }
    }

    private fun applyProcessedInput(processedInputTick: Long) {
        val confirmedComponents = replicationState.confirmInput(
            gameSession.mainPlayer.id,
            processedInputTick,
        )
        confirmedComponents.groupBy { it.entity }.forEach { (persistentId, keys) ->
            val updated = keys.mapNotNull(replicationState::authoritativeComponent)
            val removed = keys
                .filter { replicationState.authoritativeComponent(it) == null }
                .map { it.componentTypeId }
            applyEntityComponents(
                persistentId,
                updated,
                removed,
            )
        }
    }

    private fun applyWorldState(snapshot: EntityNetworkSnapshot) {
        val accepted = acceptSnapshot(ReplicationTarget.World, snapshot) ?: return

        with(replicationWorld) {
            accepted.updated.forEach {
                val component = it.revive(EntityResolver.EMPTY, componentReviveSettings) ?: return@forEach
                state.setComponent(component, runtimeComponentType(component))
            }
            removeSnapshotComponents(state, accepted.removed)
            state.networkState().revision = accepted.revision
        }
    }

    private fun applyEntityComponents(
        persistentId: PersistentId,
        updated: List<ReplicationSnapshot>,
        removed: Collection<String>,
    ): EntityId = with(replicationWorld) {
        val existing = persistentIdToEntity[persistentId]
            ?: protocolError("Сущность $persistentId отсутствует для применения снапшота")

        updated.forEach {
            val component = it.revive(EntityResolver.EMPTY, componentReviveSettings) ?: return@forEach
            componentManager.setComponentWithType(
                existing,
                component,
                runtimeComponentType(component),
            )
        }
        removeSnapshotComponents(existing, removed)

        existing
    }

    private fun World.collectPredictionEntities(): Set<PersistentId> {
        return gameSession.mainPlayer.collectReplicationEntities()
            .mapNotNullTo(mutableSetOf()) { entity ->
                if (componentManager.exists(entity)) {
                    entity.getComponent<PersistentIdComponent>()?.id
                } else {
                    null
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun World.removeSnapshotComponents(entity: EntityId, removedTypeIds: Collection<String>) {
        removedTypeIds.forEach { typeId ->
            val type = ComponentTypeRegistry.get(typeId)?.type
                ?: ScriptComponentId(EngineId(typeId)).let { scriptComponentId ->
                    componentReviveSettings.namespacedStorage.components[scriptComponentId]
                        ?: CoreScriptComponents.get(scriptComponentId)
                        ?: error("Тип компонента $typeId не существует")
                }
            removeComponent(entity, type as ComponentType<Component>)
        }
    }

    private data class AcceptedEntitySnapshot(
        val persistentId: PersistentId,
        val snapshot: SnapshotAcceptance.Accepted,
    )

    @Suppress("UNCHECKED_CAST")
    private fun runtimeComponentType(component: Component): ComponentType<Component> {
        return org.lain.cyberia.ecs.componentTypeOf(component) as ComponentType<Component>
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("Engine Client Replication")
    }
}
