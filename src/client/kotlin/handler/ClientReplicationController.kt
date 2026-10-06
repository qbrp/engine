package org.lain.engine.client.handler

import org.lain.cyberia.ecs.*
import org.lain.engine.client.GameSession
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.data.EntityResolver
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.VoxelPosId
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.server.protocolError
import org.lain.engine.server.replication.*
import org.lain.engine.transport.packet.InitialReplicationState
import org.lain.engine.transport.packet.ReplicationEntityMetadata
import org.lain.engine.transport.packet.ReplicationPacket
import org.lain.engine.transport.packet.ReplicationResyncRequestPacket
import org.lain.engine.transport.packet.SERVERBOUND_REPLICATION_RESYNC_REQUEST_ENDPOINT
import org.lain.engine.util.EntityDebugNameId
import org.lain.engine.util.Log
import org.lain.engine.util.LogLevel
import org.lain.engine.util.LogMessages
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.getDebugId
import org.lain.engine.util.getEntityDebugNameId
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.World
import org.slf4j.LoggerFactory

class ClientReplicationController(
    private val gameSession: GameSession,
    initialReplicationState: InitialReplicationState,
) : EntityResolver {
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

    fun beginPrediction(inputTick: Long) {
        replicationState.beginInteraction(inputTick, replicationWorld.collectPredictionEntities())
    }

    fun endPrediction() {
        replicationState.endInteraction()
    }

    fun apply(packet: ReplicationPacket) {
        try {
            applyPacket(packet)
        } catch (exception: Exception) {
            LOGGER.error("Не удалось применить кадр репликации", exception)
            val errorString = StringBuilder(
                "<bold>Ошибка применения кадра репликации</bold><newline>"
            )
            errorString.append(
                "Игровая сессия была прекращена из-за непредвиденного исключения. " +
                    "Свяжитесь с разработчиком и сообщите о возникшем сбое.<newline>"
            )
            appendExceptionString(errorString, 0, exception)
            gameSession.client.infrastructure.disconnect(errorString.toString())
        }
    }

    private fun appendExceptionString(string: StringBuilder, indent: Int, exception: Exception) {
        string.append("  ".repeat(indent))
        string.append("- " + (exception.message ?: exception.toString()))
        string.append("<newline>")
        exception.cause?.let {
            appendExceptionString(string, indent + 1, exception)
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

    private fun unloadEntity(persistentId: PersistentId) {
        with(gameSession.world) {
            persistentIdToEntity[persistentId]
                ?.takeIf(componentManager::exists)
                ?.destroy()
        }

        removeEntity(persistentId)

        if (persistentId is VoxelPosId) {
            replicationWorld.chunkStorage.removeVoxel(persistentId.pos)
        }
    }

    private fun applyPacket(packet: ReplicationPacket) {
        val frame = packet.frame
        frame.out.forEach { persistentId -> unloadEntity(persistentId) }
        frame.world?.let { applyWorldState(it) }

        val acceptedSnapshots = frame.entities.mapNotNull { (id, snapshot) ->
            val metadata = packet.entityMetadata[id]
                ?: protocolError("В пакете репликации отсутствуют метаданные сущности $id")
            acceptUpdate(id, metadata, snapshot)
        }

        acceptedSnapshots.forEach { ensureEntity(it.persistentId) }
        acceptedSnapshots.forEach(::applyEntitySnapshot)

        frame.processedInputTick?.let { processedInputTick ->
            applyProcessedInput(processedInputTick)
        }
    }

    private fun acceptUpdate(
        persistentId: PersistentId,
        metadata: ReplicationEntityMetadata,
        snapshot: EntityStateUpdate,
    ): AcceptedEntityStateUpdate? {
        if (snapshot is EntityStateUpdate.Delta && replicationWorld.persistentIdToEntity[persistentId] == null) {
            protocolError("Получен частичный снапшот отсутствующей сущности $persistentId")
        }

        val accepted = acceptSnapshot(ReplicationTarget.Entity(persistentId), snapshot)
            ?: return null

        val baseRevision = when (snapshot) {
            is EntityStateUpdate.Delta -> snapshot.delta.baseRevision
            is EntityStateUpdate.Full -> null
        }
        return AcceptedEntityStateUpdate(persistentId, metadata, baseRevision, accepted)
    }

    private fun acceptSnapshot(
        target: ReplicationTarget,
        snapshot: EntityStateUpdate,
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
                    SERVERBOUND_REPLICATION_RESYNC_REQUEST_ENDPOINT.sendC2SPacket(
                        ReplicationResyncRequestPacket(target)
                    )
                }
                return null
            }

            is SnapshotAcceptance.Accepted -> acceptance
        }

        if (snapshot is EntityStateUpdate.Full) {
            targetsAwaitingResync.remove(target)
        }

        return accepted
    }

    private fun applyEntitySnapshot(acceptedSnapshot: AcceptedEntityStateUpdate) = with(replicationWorld) {
        val persistentId = acceptedSnapshot.persistentId
        val entity = persistentIdToEntity[persistentId]
            ?: protocolError("Сущность $persistentId отсутствует для применения снапшота")
        val debugId = acceptedSnapshot.metadata.debugName
            ?.let { EntityDebugNameId(it, entity) }
            ?: entity.getDebugId()

        try {
            val snapshot = acceptedSnapshot.snapshot
            val updated = snapshot.updated.filterNot { component ->
                replicationState.isPredicted(persistentId, component.id)
            }
            val removed = snapshot.removed.filterNot { componentTypeId ->
                replicationState.isPredicted(persistentId, componentTypeId)
            }
            val appliedEntity = applyEntityComponents(
                persistentId,
                updated,
                removed,
            )
            appliedEntity.networkState().revision = snapshot.revision

            gameSession.logInMainThread {
                Log(
                    LogMessages.ENTITY_SYNC_ADD,
                    LogLevel.INFO,
                    data = mapOf(
                        "entity" to appliedEntity.getEntityDebugNameId().name,
                        "persistent_id" to persistentId.toString(),
                        "components" to updated.joinToString(),
                    ),
                    world = world.id,
                    tick = it,
                )
            }
        } catch (e: Exception) {
            throw ReplicationSnapshotApplyException(
                debugId = debugId,
                entityPersistentId = acceptedSnapshot.persistentId,
                baseRevision = acceptedSnapshot.baseRevision,
                targetRevision = acceptedSnapshot.snapshot.revision,
                cause = e,
            )
        }
    }

    private fun applyProcessedInput(processedInputTick: Long) {
        val confirmedComponents = replicationState.confirmInput(processedInputTick)
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

    private fun applyWorldState(snapshot: EntityStateUpdate) {
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
            val component = try {
                it.revive(EntityResolver.EMPTY, componentReviveSettings) ?: return@forEach
            } catch (e: Exception) {
                throw ComponentReviveException(it.id, cause = e)
            }
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
            try {
                val type = ComponentTypeRegistry.get(typeId)?.type
                    ?: ScriptComponentId(EngineId(typeId)).let { scriptComponentId ->
                        componentReviveSettings.namespacedStorage.components[scriptComponentId]
                            ?: CoreScriptComponents.get(scriptComponentId)
                            ?: error("Тип компонента $typeId не существует")
                    }
                removeComponent(entity, type as ComponentType<Component>)
            } catch (e: Exception) {
                throw ComponentRemoveException(typeId, e)
            }
        }
    }

    private data class AcceptedEntityStateUpdate(
        val persistentId: PersistentId,
        val metadata: ReplicationEntityMetadata,
        val baseRevision: Long?,
        val snapshot: SnapshotAcceptance.Accepted,
    )

    @Suppress("UNCHECKED_CAST")
    private fun runtimeComponentType(component: Component): ComponentType<Component> {
        return componentTypeOf(component) as ComponentType<Component>
    }

    override fun find(persistentId: PersistentId): org.lain.cyberia.ecs.EntityId? {
        return replicationWorld.persistentIdToEntity[persistentId]
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("Engine Client Replication")
    }
}
