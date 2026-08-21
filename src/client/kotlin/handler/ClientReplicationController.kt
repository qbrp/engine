package org.lain.engine.client.handler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.lain.cyberia.ecs.*
import org.lain.engine.client.GameSession
import org.lain.engine.player.PlayerContainer
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.server.EntityNetworkSnapshot
import org.lain.engine.server.Networked
import org.lain.engine.server.networkState
import org.lain.engine.storage.*
import org.lain.engine.util.*
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.EngineChunk
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.Event
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

internal class ClientReplicationController(
    private val coroutineScope: CoroutineScope,
    private val awaitChunk: suspend (GameSession, EngineChunkPos) -> EngineChunk,
    private val requestEntityResync: (PersistentId) -> Unit,
) {
    private val replicationState = ClientReplicationState()
    private var replicationWorld: World? = null
    private var worldStateRevision: Long? = null
    private val worldStateComponents = linkedMapOf<String, ComponentDto>()
    private val pendingResyncRequests = mutableSetOf<PersistentId>()

    private val awaitingEntities =
        ConcurrentHashMap<PersistentId, CompletableDeferred<PendingEntity?>>()
    private val pendingEntityProvider = PendingEntityProvider(awaitingEntities)
    private val entitySnapshotQueues = mutableMapOf<PersistentId, ArrayDeque<PendingEntitySnapshot>>()
    private val entitySnapshotJobs = mutableMapOf<PersistentId, Job>()
    private var requestedProcessedInputTick = -1L
    private var processedInputJob: Job? = null

    fun initialize(gameSession: GameSession) = with(gameSession.world) {
        replicationState.clear()
        worldStateRevision = null
        worldStateComponents.clear()
        pendingResyncRequests.clear()
        processedInputJob?.cancel()
        processedInputJob = null
        requestedProcessedInputTick = -1
        replicationWorld?.let { previousWorld ->
            previousWorld.componentManager.networkedComponentChangeListener = null
        }
        replicationWorld = this
        componentManager.networkedComponentChangeListener = listener@{ entity, type ->
            val persistentId = entity.getComponent<PersistentIdComponent>()?.id ?: return@listener
            replicationState.recordComponentChange(persistentId, type.id)
        }

        iterate<Networked, PersistentIdComponent> { entity, _, (persistentId) ->
            val components = runCatching {
                componentManager.getNetworkedComponents(entity).map { it.toSnapshotDto() }
            }.getOrElse { exception ->
                LOGGER.warn("Failed to seed authoritative state for $persistentId", exception)
                emptyList()
            }
            replicationState.seedEntity(persistentId, components)
        }
        componentManager.getNetworkedComponents(this.state)
            .map { it.toSnapshotDto() }
            .forEach { worldStateComponents[it.id] = it }
    }

    fun beginPrediction(
        world: World,
        playerEntity: EntityId,
        interactionId: InteractionId,
    ) {
        replicationState.beginInteraction(interactionId, world.collectPredictionEntities(playerEntity))
    }

    fun beginPrediction(interactionId: InteractionId, entities: Set<PersistentId>) {
        replicationState.beginInteraction(interactionId, entities)
    }

    fun endPrediction() {
        replicationState.endInteraction()
    }

    fun disable(world: World) {
        replicationWorld?.componentManager?.networkedComponentChangeListener = null
        if (replicationWorld !== world) {
            world.componentManager.networkedComponentChangeListener = null
        }
        replicationWorld = null

        replicationState.clear()
        worldStateRevision = null
        worldStateComponents.clear()
        pendingResyncRequests.clear()
        processedInputJob?.cancel()
        processedInputJob = null
        requestedProcessedInputTick = -1
        entitySnapshotJobs.values.forEach { it.cancel() }
        entitySnapshotJobs.clear()
        entitySnapshotQueues.clear()
        awaitingEntities.values.forEach { it.cancel() }
        awaitingEntities.clear()
        pendingEntityProvider.clear()
    }

    fun removeEntity(persistentId: PersistentId) {
        replicationState.removeEntity(persistentId)
        pendingResyncRequests.remove(persistentId)
    }

    fun applyEntity(
        gameSession: GameSession,
        persistentId: PersistentId,
        snapshot: EntityNetworkSnapshot,
    ) {
        if (snapshot is EntityNetworkSnapshot.Full) {
            pendingResyncRequests.remove(persistentId)
        }
        val accepted = when (val acceptance = replicationState.accept(persistentId, snapshot)) {
            SnapshotAcceptance.Ignored -> return
            is SnapshotAcceptance.Gap -> {
                LOGGER.warn(
                    "Entity $persistentId revision gap: local=${acceptance.currentRevision}, " +
                        "incoming=${acceptance.baseRevision}->${acceptance.revision}"
                )
                if (pendingResyncRequests.add(persistentId)) {
                    requestEntityResync(persistentId)
                }
                return
            }

            is SnapshotAcceptance.Accepted -> acceptance
        }

        val snapshots = entitySnapshotQueues.getOrPut(persistentId) { ArrayDeque() }
        snapshots.addLast(PendingEntitySnapshot(gameSession, accepted))

        val pendingEntity = awaitingEntities.computeIfAbsent(persistentId) { CompletableDeferred() }
        pendingEntity.complete(PendingEntity(accepted.authoritativeComponents))

        if (entitySnapshotJobs[persistentId]?.isActive == true) return

        entitySnapshotJobs[persistentId] = coroutineScope.launch {
            try {
                while (snapshots.isNotEmpty()) {
                    val pendingSnapshot = snapshots.removeFirst()
                    try {
                        applyEntitySnapshot(persistentId, pendingSnapshot)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LOGGER.error(
                            "Не удалось применить сетевой снапшот сущности $persistentId",
                            e,
                        )
                    }
                }
            } finally {
                entitySnapshotQueues.remove(persistentId)
                entitySnapshotJobs.remove(persistentId)
                awaitingEntities.remove(persistentId, pendingEntity)
            }
        }
    }

    fun applyProcessedInput(gameSession: GameSession, processedInputTick: Long) {
        if (
            processedInputTick <= replicationState.processedInputTick ||
            processedInputTick <= requestedProcessedInputTick
        ) {
            return
        }
        requestedProcessedInputTick = processedInputTick
        if (processedInputJob?.isActive == true) return

        processedInputJob = coroutineScope.launch {
            while (replicationState.processedInputTick < requestedProcessedInputTick) {
                val targetInputTick = requestedProcessedInputTick
                entitySnapshotJobs.values.toList().joinAll()
                val confirmedComponents = replicationState.confirmInput(
                    gameSession.mainPlayer.id,
                    targetInputTick,
                )
                confirmedComponents.groupBy { it.entity }.forEach { (persistentId, keys) ->
                    val updated = keys.mapNotNull(replicationState::authoritativeComponent)
                    val removed = keys
                        .filter { replicationState.authoritativeComponent(it) == null }
                        .map { it.componentTypeId }
                    applyEntityComponents(
                        gameSession,
                        persistentId,
                        updated,
                        removed,
                        replicationState.authoritativeComponents(persistentId),
                    )
                }
            }
        }
    }

    fun applyWorldState(gameSession: GameSession, snapshot: EntityNetworkSnapshot) =
        with(gameSession.world) {
            val (updated, removed) = when (snapshot) {
                is EntityNetworkSnapshot.Full -> {
                    val revision = worldStateRevision
                    if (revision != null && snapshot.revision < revision) return@with
                    val newComponents = snapshot.components.associateBy { it.id }
                    val removed = worldStateComponents.keys - newComponents.keys
                    worldStateComponents.clear()
                    worldStateComponents.putAll(newComponents)
                    worldStateRevision = snapshot.revision
                    snapshot.components to removed
                }

                is EntityNetworkSnapshot.Delta -> {
                    val revision = worldStateRevision
                    if (revision != null && snapshot.revision <= revision) return@with
                    if (snapshot.baseRevision != revision) {
                        LOGGER.warn(
                            "World-state revision gap: local=$revision, " +
                                "incoming=${snapshot.baseRevision}->${snapshot.revision}"
                        )
                        return@with
                    }
                    snapshot.delta.updated.forEach { worldStateComponents[it.id] = it }
                    snapshot.delta.removed.forEach(worldStateComponents::remove)
                    worldStateRevision = snapshot.revision
                    snapshot.delta.updated to snapshot.delta.removed
                }
            }

            runBlocking {
                state.copyComponentDtoState(updated) {
                    toDomainWithoutRelationships(
                        itemStorage,
                        gameSession.namespacedStorage,
                        gameSession.luaContext,
                    )
                }
            }
            removeSnapshotComponents(state, removed)
            state.networkState().revision = worldStateRevision ?: 0
        }

    fun applyItemUnload(gameSession: GameSession, items: List<PersistentId>) =
        with(gameSession.world) {
            items.forEach { item ->
                removeEntity(item)
                gameSession.itemStorage.remove(item)?.destroy()
            }
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

    private suspend fun applyEntitySnapshot(
        persistentId: PersistentId,
        pendingSnapshot: PendingEntitySnapshot,
    ) = with(pendingSnapshot.gameSession.world) {
        val snapshot = pendingSnapshot.snapshot
        val updated = snapshot.updated.filterNot { component ->
            replicationState.isPredicted(persistentId, component.id)
        }
        val removed = snapshot.removed.filterNot { componentTypeId ->
            replicationState.isPredicted(persistentId, componentTypeId)
        }
        val entity = applyEntityComponents(
            pendingSnapshot.gameSession,
            persistentId,
            updated,
            removed,
            snapshot.authoritativeComponents,
        ) ?: return@with
        entity.networkState().revision = snapshot.revision

        pendingSnapshot.gameSession.logInMainThread {
            Log(
                LogMessages.ENTITY_SYNC_ADD,
                LogLevel.INFO,
                data = mapOf(
                    "entity" to entity.getEntityDebugNameId().name,
                    "persistent_id" to persistentId.toString(),
                    "components" to updated.joinToString(),
                ),
                world = pendingSnapshot.gameSession.world.id,
                tick = it,
            )
        }
        if (snapshot.authoritativeComponents.any { it.id == componentTypeOf(Event::class).id }) {
            replicationState.removeEntity(persistentId)
        }
    }

    private suspend fun applyEntityComponents(
        gameSession: GameSession,
        persistentId: PersistentId,
        updated: List<ComponentDto>,
        removed: Collection<String>,
        fallbackComponents: List<ComponentDto> = emptyList(),
    ): EntityId? = with(gameSession.world) {
        val existing = persistentIdToEntity[persistentId]
        val components = when {
            updated.isNotEmpty() -> updated
            existing == null -> fallbackComponents.filterNot { component ->
                replicationState.isPredicted(persistentId, component.id)
            }

            else -> emptyList()
        }
        val entity = when {
            components.isNotEmpty() -> EntityResolver(pendingEntityProvider).loadEntity(
                componentLoadSettings,
                components,
                persistentId,
            )

            else -> existing
        } ?: return@with null

        removeSnapshotComponents(entity, removed)
        if (persistentId is VoxelPosId) {
            val chunk = awaitChunk(gameSession, EngineChunkPos(persistentId.pos))
            chunk.dynamicVoxels[persistentId.pos] = entity
        }
        entity
    }

    @Suppress("UNCHECKED_CAST")
    private fun World.removeSnapshotComponents(entity: EntityId, removedTypeIds: Collection<String>) {
        removedTypeIds.forEach { typeId ->
            val type = ComponentTypeRegistry.get(typeId)?.type
                ?: ScriptComponentId(typeId).let { scriptComponentId ->
                    componentLoadSettings.namespacedStorage.components[scriptComponentId]
                        ?: CoreScriptComponents.get(scriptComponentId)
                        ?: error("Тип компонента $typeId не существует")
                }
            removeComponent(entity, type as ComponentType<Component>)
        }
    }

    private data class PendingEntitySnapshot(
        val gameSession: GameSession,
        val snapshot: SnapshotAcceptance.Accepted,
    )

    private companion object {
        val LOGGER = LoggerFactory.getLogger("Engine Client Replication")
    }
}
