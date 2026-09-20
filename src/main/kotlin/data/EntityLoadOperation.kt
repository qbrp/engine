package org.lain.engine.data

import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.Log
import org.lain.engine.util.LogDiagnosticContext
import org.lain.engine.util.LogLevel
import org.lain.engine.util.ecs.traverseEntityGraph
import java.util.concurrent.CancellationException

class EntityLoadException(
    val entity: PersistentId,
    cause: Throwable? = null,
    message: String? = null,
) : RuntimeException("Не удалось загрузить сущность $entity: $message", cause)

class EntityNotExistsException(val entity: PersistentId) : RuntimeException("Сущность $entity не существует")

sealed interface EntityLoadResult {
    data class Success(
        val entity: EntityId
    ) : EntityLoadResult

    data class Failure(
        val error: Throwable
    ) : EntityLoadResult

    fun getEntityOrThrow(): EntityId {
        return when(this) {
            is Failure -> throw error
            is Success -> entity
        }
    }
}

class EntityLoadOperation(
    val uuid: PersistentId,
    private val database: Database,
    private val context: TransactionContext,
) : EntityResolver {
    private val discovered = mutableMapOf<PersistentId, DiscoveredEntity>()
    private val unresolvedComponents = mutableListOf<ComponentBatchDto>()
    private val world
        get() = context.world
    private val coordinator
        get() = context.coordinator

    context(diagnostics: LogDiagnosticContext)
    suspend fun execute(): EntityLoadResult {
        return try {
            when (val root = discoverEntity(uuid)) {
                is DiscoveredEntity.Exists -> EntityLoadResult.Success(root.entityId)
                is DiscoveredEntity.Acquired -> {
                    traverseEntityGraph(
                        root.record.uuid,
                        root.record.relations
                    ) {
                        when (val child = discoverEntity(it)) {
                            is DiscoveredEntity.Exists -> emptyList()
                            is DiscoveredEntity.Acquired -> child.record.relations
                        }
                    }

                    discovered.values
                        .filterIsInstance<DiscoveredEntity.Acquired>()
                        .forEach { loadEntityComponents(it) }

                    saveUnresolvedComponents()

                    EntityLoadResult.Success(root.entityId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOGGER.error("Не удалось выполнить {}", this, e)
            EntityLoadResult.Failure(e)
         }
    }

    private suspend fun discoverEntity(
        persistentId: PersistentId
    ): DiscoveredEntity {
        discovered[persistentId]?.let { return it }

        // когда в движке появятся произвольные связи, здесь может быть дедлок
        val entity = when (val result = coordinator.acquire(world, persistentId)) {
            is AcquireResult.Acquired -> {
                val (reservation) = result
                val pending = context.adoptEntityLoad(reservation)

                val record = database.loadEntity(persistentId)
                    ?: throw EntityNotExistsException(persistentId)
                val e = context.world.addEntity()
                pending.bind(e)

                DiscoveredEntity.Acquired(e, record)
            }

            is AcquireResult.Loading -> {
                when (val entityLoadResult = result.deferred.await()) {
                    is EntityLoadResult.Success -> discoverEntity(persistentId)
                    is EntityLoadResult.Failure -> {
                        throw EntityLoadException(persistentId, entityLoadResult.error)
                    }
                }
            }

            is AcquireResult.Leased -> {
                context.registerEntityLease(result.lease)
                DiscoveredEntity.Exists(result.lease.entity)
            }

            is AcquireResult.Unloading -> {
                result.deferred.await()
                discoverEntity(persistentId) // возможно грязновато, но пока сойдет
            }
        }

        discovered[persistentId] = entity
        return entity
    }

    context(diagnostics: LogDiagnosticContext)
    private fun loadEntityComponents(entity: DiscoveredEntity.Acquired) = with(context.commands) {
        val entityId = entity.entityId
        val entityRecord = entity.record
        try {
            val materializedComponents = entityRecord.materialize(world.componentLoadSettings, this@EntityLoadOperation)
            unresolvedComponents += materializedComponents.unresolved
            materializedComponents.apply(entityId)

            EngineLogger.logContextual(
                Log(
                    "Transaction entity loaded",
                    LogLevel.INFO,
                    data = mutableMapOf(
                        "uuid" to entityRecord.uuid.toString(),
                        "resolved_components" to materializedComponents.resolved.joinToString(),
                        "unresolved_components" to materializedComponents.unresolved.joinToString(),
                        "data" to entityRecord.data.toString()
                    ),
                    tick = world.simulation.ticks,
                    world = world.id
                )
            )
        } catch (e: Exception) {
            throw EntityLoadException(entity.record.uuid, e)
        }
    }

    private suspend fun saveUnresolvedComponents() {
        if (unresolvedComponents.isEmpty()) return
        try {
            database.upsertComponentsBatchTransaction(unresolvedComponents)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOGGER.error(
                "Не удалось сохранить {} незагруженных компонентов в рамках транзакции {}",
                unresolvedComponents.size,
                this.toString(),
                e
            )
        }
    }

    override fun find(persistentId: PersistentId): EntityId? {
        return discovered[persistentId]?.entityId
    }

    override fun require(persistentId: PersistentId): EntityId {
        return find(persistentId) ?: error("Сущность $persistentId не была загружена")
    }

    override fun toString(): String {
        return "EntityLoadTransaction(world='${world.id}', root_entity='$uuid)"
    }

    sealed interface DiscoveredEntity {
        val entityId: EntityId

        data class Acquired(
            override val entityId: EntityId,
            val record: EntityPersistentRecord
        ) : DiscoveredEntity

        data class Exists(
            override val entityId: EntityId,
        ) : DiscoveredEntity
    }
}