package org.lain.engine.client.handler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.GameSession
import org.lain.engine.player.PlayerContainer
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.server.EntityNetworkSnapshot
import org.lain.engine.server.Networked
import org.lain.engine.server.ReplicationFrameSnapshot
import org.lain.engine.server.ReplicationTarget
import org.lain.engine.server.desync
import org.lain.engine.server.networkState
import org.lain.engine.data.EntityProvider
import org.lain.engine.data.EntityResolver
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.VoxelPosId
import org.lain.engine.data.copyComponentDtoState
import org.lain.engine.data.toDomainWithoutRelationships
import org.lain.engine.util.Log
import org.lain.engine.util.LogLevel
import org.lain.engine.util.LogMessages
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.getEntityDebugNameId
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.World
import org.slf4j.LoggerFactory

class ClientReplicationController(
    private val gameSession: GameSession,
    coroutineScope: CoroutineScope,
    private val requestResync: (ReplicationTarget) -> Unit,
) {
    private val replicationWorld: World = gameSession.world
    private val replicationState = ClientReplicationState()
    private val targetsAwaitingResync = mutableSetOf<ReplicationTarget>()

    private val frames = Channel<ReplicationFrameSnapshot>(Channel.UNLIMITED)
    private val frameJob: Job

    private val missingEntityProvider = object : EntityProvider {
        override suspend fun loadEntity(persistentId: PersistentId): List<ComponentDto> =
            desync("Сетевой снапшот ссылается на отсутствующую сущность $persistentId")
    }

    init {
        with(replicationWorld) {
            componentManager.networkedComponentChangeListener = listener@{ entity, type ->
                val persistentId =
                    entity.getComponent<PersistentIdComponent>()?.id ?: return@listener
                replicationState.recordComponentChange(persistentId, type.id)
            }
            componentManager.entityDestroyedListener = { _, persistentId ->
                persistentId?.let(::removeEntity)
            }

            iterate<Networked, PersistentIdComponent> { entity, _, (persistentId) ->
                val components = runCatching {
                    componentManager.getNetworkedComponents(entity).map { it.snapshotDto() }
                }.getOrElse { exception ->
                    LOGGER.warn("Failed to seed authoritative state for $persistentId", exception)
                    emptyList()
                }
                replicationState.seed(ReplicationTarget.Entity(persistentId), components)
            }

            replicationState.seed(
                ReplicationTarget.World,
                componentManager.getNetworkedComponents(state).map { it.snapshotDto() },
            )
        }
        frameJob = coroutineScope.launch { consumeFrames() }
    }

    fun enqueue(frame: ReplicationFrameSnapshot) {
        val result = frames.trySend(frame)
        if (result.isFailure) {
            throw IllegalStateException(
                "Не удалось добавить сетевой кадр в очередь",
                result.exceptionOrNull(),
            )
        }
    }

    fun beginPrediction(
        playerEntity: EntityId,
        interactionId: InteractionId,
    ) {
        replicationState.beginInteraction(
            interactionId,
            replicationWorld.collectPredictionEntities(playerEntity),
        )
    }

    fun endPrediction() {
        replicationState.endInteraction()
    }

    fun removeEntity(persistentId: PersistentId) {
        replicationState.removeEntity(persistentId)
        targetsAwaitingResync.remove(ReplicationTarget.Entity(persistentId))
    }

    fun applyItemUnload(items: List<PersistentId>) = with(replicationWorld) {
        items.forEach { item ->
            gameSession.itemStorage.remove(item)?.destroy() ?: removeEntity(item)
        }
    }

    fun close() {
        replicationWorld.componentManager.networkedComponentChangeListener = null
        replicationWorld.componentManager.entityDestroyedListener = null
        frames.close()
        frameJob.cancel()
        replicationState.clear()
        targetsAwaitingResync.clear()
    }

    private suspend fun consumeFrames() {
        try {
            for (frame in frames) {
                applyFrame(frame)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            frames.close(e)
            LOGGER.error("Не удалось применить сетевой кадр репликации", e)
            gameSession.client.infrastructure.disconnect(
                e.message ?: "Не удалось применить сетевой кадр репликации",
            )
        }
    }

    private suspend fun applyFrame(frame: ReplicationFrameSnapshot) {
        val acceptedSnapshots = frame.entities.mapNotNull { (persistentId, snapshot) ->
            acceptEntitySnapshot(persistentId, snapshot)
        }

        with(replicationWorld) {
            acceptedSnapshots.forEach { snapshot ->
                if (persistentIdToEntity[snapshot.persistentId] == null) {
                    instantiateEntity(snapshot.persistentId, emptyList())
                }
            }
        }

        frame.world?.let { applyWorldState(it) }

        val resolver = EntityResolver(missingEntityProvider)
        acceptedSnapshots.forEach { pending ->
            applyEntitySnapshot(pending, resolver)
        }

        frame.processedInputTick?.let { processedInputTick ->
            applyProcessedInput(processedInputTick, resolver)
        }
    }

    private fun acceptEntitySnapshot(
        persistentId: PersistentId,
        snapshot: EntityNetworkSnapshot,
    ): AcceptedEntitySnapshot? {
        if (
            snapshot is EntityNetworkSnapshot.Delta &&
            replicationWorld.persistentIdToEntity[persistentId] == null
        ) {
            desync("Получен частичный снапшот отсутствующей сущности $persistentId")
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

    private suspend fun applyEntitySnapshot(
        acceptedSnapshot: AcceptedEntitySnapshot,
        resolver: EntityResolver,
    ) = with(replicationWorld) {
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
            resolver,
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

    private suspend fun applyProcessedInput(
        processedInputTick: Long,
        resolver: EntityResolver,
    ) {
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
                resolver,
            )
        }
    }

    private suspend fun applyWorldState(snapshot: EntityNetworkSnapshot) {
        val accepted = acceptSnapshot(ReplicationTarget.World, snapshot) ?: return

        with(replicationWorld) {
            state.copyComponentDtoState(accepted.updated) {
                toDomainWithoutRelationships(componentLoadSettings)
            }
            removeSnapshotComponents(state, accepted.removed)
            state.networkState().revision = accepted.revision
        }
    }

    private suspend fun applyEntityComponents(
        persistentId: PersistentId,
        updated: List<ComponentDto>,
        removed: Collection<String>,
        resolver: EntityResolver,
    ): EntityId = with(replicationWorld) {
        val existing = persistentIdToEntity[persistentId]
            ?: desync("Сущность $persistentId отсутствует при применении снапшота")

        val entity = if (updated.isNotEmpty()) {
            resolver.loadEntity(
                componentLoadSettings,
                updated,
                persistentId,
            )
        } else {
            existing
        }

        removeSnapshotComponents(entity, removed)
        if (persistentId is VoxelPosId) {
            val chunkPos = EngineChunkPos(persistentId.pos)
            val chunk = chunkStorage.getChunk(chunkPos)
                ?: desync("Получена динамическая воксельная сущность $persistentId на непрогруженном $chunkPos")
            chunk.dynamicVoxels[persistentId.pos] = entity
        }
        entity
    }

    private fun World.collectPredictionEntities(playerEntity: EntityId): Set<PersistentId> {
        val inventory = playerEntity.getComponent<PlayerInventory>()
        val playerContainer = playerEntity.getComponent<PlayerContainer>()
        return buildSet {
            add(playerEntity)
            inventory?.let {
                addAll(inventory.items)
                inventory.cursorItem?.let(::add)
                inventory.mainHandItem?.let(::add)
                inventory.offHandItem?.let(::add)
            }
            playerContainer?.let { add(it.containerId) }
        }.mapNotNullTo(mutableSetOf()) { entity ->
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
                    componentLoadSettings.namespacedStorage.components[scriptComponentId]
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

    private companion object {
        val LOGGER = LoggerFactory.getLogger("Engine Client Replication")
    }
}
