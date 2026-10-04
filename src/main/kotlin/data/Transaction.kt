package org.lain.engine.data

import org.lain.engine.util.ecs.EntityCommandBuffer
import org.lain.engine.world.World

import kotlinx.coroutines.CompletableDeferred
import org.lain.cyberia.ecs.destroy
import org.lain.engine.script.CallbackType
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.EngineServer
import org.lain.engine.util.ecs.EntityId
import kotlin.collections.plusAssign

enum class TransactionState {
    ACTIVE,
    CLOSING,
    COMMITTING,
    COMMIT_FAILED,
    COMMITTED,
    MERGED,
    ROLLED_BACK,
}

internal class TransactionTree {
    val lock = Any()
}

class PendingEntityLoad internal constructor(
    val reservation: EntityLoadReservation,
) {
    var migrations: List<PlannedMigration>? = null
        private set
    var entity: EntityId? = null
        private set
    var persistentData: EntityPersistenceData? = null
        private set

    fun bind(entity: EntityId, persistenceData: EntityPersistenceData?) {
        check(this.entity == null)
        this.entity = entity
        this.persistentData = persistenceData
    }

    fun planMigration(migrations: List<PlannedMigration>) {
        check(this.migrations == null)
        this.migrations = migrations.toList()
    }
}

class TransactionContext internal constructor(
    val world: World,
    private val server: EngineServer,
    private val parent: TransactionContext? = null,
    private val tree: TransactionTree = TransactionTree(),
) {
    val coordinator = server.entityCoordinator
    val commands = EntityCommandBuffer(world)

    private val children = mutableSetOf<TransactionContext>()
    private val entityLoads = mutableListOf<PendingEntityLoad>()
    private val entityLeases = mutableListOf<EntityLease>()

    private var _state = TransactionState.ACTIVE

    val state: TransactionState
        get() = synchronized(tree.lock) {
            _state
        }

    private var childrenFinished = CompletableDeferred<Unit>().apply {
        complete(Unit)
    }

    fun adoptEntityLoad(
        reservation: EntityLoadReservation,
    ): PendingEntityLoad {
        try {
            return synchronized(tree.lock) {
                checkActive()

                PendingEntityLoad(reservation).also {
                    entityLoads += it
                }
            }
        } catch (e: Throwable) {
            try {
                coordinator.abortLoadReservation(reservation, e)
            } catch (abortFailure: Throwable) {
                e.addCleanupFailure(abortFailure)
            }
            throw e
        }
    }

    fun registerEntityLease(lease: EntityLease) {
        try {
            synchronized(tree.lock) {
                checkActive()
                entityLeases += lease
            }
        } catch (cause: Throwable) {
            try {
                coordinator.releaseEntity(lease)
            } catch (releaseFailure: Throwable) {
                cause.addCleanupFailure(releaseFailure)
            }
            throw cause
        }
    }

    fun child(): TransactionContext = synchronized(tree.lock) {
        checkActive()

        if (children.isEmpty()) {
            childrenFinished = CompletableDeferred()
        }

        TransactionContext(world, server, this, tree)
            .also { children += it }
    }

    suspend fun awaitChildren() {
        val signal = synchronized(tree.lock) {
            when (_state) {
                TransactionState.ACTIVE -> {
                    _state = TransactionState.CLOSING
                }
                else -> error(
                    "Cannot await children while transaction is $_state"
                )
            }

            childrenFinished
        }

        signal.await()
    }

    fun commit() {
        val loads = mutableListOf<Pair<PendingEntityLoad, EntityId>>()
        val leases = mutableListOf<EntityLease>()

        synchronized(tree.lock) {
            check(
                _state == TransactionState.ACTIVE ||
                        _state == TransactionState.CLOSING
            ) {
                "Cannot commit transaction while transaction is $_state"
            }

            check(children.isEmpty()) {
                "Cannot commit transaction with active children"
            }

            if (parent != null) {
                check(
                    parent._state == TransactionState.ACTIVE ||
                            parent._state == TransactionState.CLOSING
                ) {
                    "Cannot merge transaction into parent while parent is ${parent._state}"
                }

                parent.commands.merge(commands)
                parent.entityLoads.addAll(entityLoads)
                parent.entityLeases.addAll(entityLeases)
                parent.removeChildLocked(this)

                _state = TransactionState.MERGED
                return
            }

            check(server.isOnThread()) {
                "Root transaction must be committed on the server thread"
            }

            loads += entityLoads.map { load ->
                val entity = checkNotNull(load.entity) {
                    "Cannot commit transaction with unfinished entity load " +
                            load.reservation.uuid
                }
                load to entity
            }
            leases += entityLeases

            _state = TransactionState.COMMITTING
        }

        try {
            loads.forEach { (load, _) ->
                load.migrations?.forEach(PlannedMigration::migrate)
            }
        } catch (cause: Throwable) {
            try {
                rollback(cause, allowCommitting = true)
            } catch (rollbackFailure: Throwable) {
                cause.addSuppressed(rollbackFailure)
            }
            throw cause
        }

        var publishedLoadCount = 0
        try {
            commands.apply()

            loads.forEach { (load, entity) ->
                world.simulation.callbacks.of(CallbackType.ENTITY_MATERIALIZATION)?.execute(
                    ScriptContext.EntityMaterialization(
                        entity,
                        world,
                        load.reservation.uuid,
                        load.persistentData!!
                    )
                )
            }

            loads.forEach { (load, entity) ->
                coordinator.completeLoadReservation(load.reservation, entity)
                publishedLoadCount++
            }
        } catch (cause: Throwable) {
            setCommitState(TransactionState.COMMIT_FAILED)
            commands.clear()

            val unfinishedLoads = loads.drop(publishedLoadCount).map { it.first }
            abortLoads(unfinishedLoads, cause)
            destroyLoads(unfinishedLoads, cause)
            releaseLeases(leases, cause)
            throw cause
        }

        val releaseFailure = releaseLeases(leases)
        if (releaseFailure != null) {
            setCommitState(TransactionState.COMMIT_FAILED)
            throw releaseFailure
        }

        setCommitState(TransactionState.COMMITTED)
    }

    fun rollback(cause: Throwable) = rollback(cause, allowCommitting = false)

    private fun rollback(cause: Throwable, allowCommitting: Boolean) {
        val leases = mutableListOf<EntityLease>()
        val loads = mutableListOf<PendingEntityLoad>()

        synchronized(tree.lock) {
            check(_state != TransactionState.MERGED) {
                "Merged transaction cannot be rolled back independently"
            }

            if (_state == TransactionState.ROLLED_BACK) {
                return
            }

            check(
                _state == TransactionState.ACTIVE ||
                        _state == TransactionState.CLOSING ||
                        (allowCommitting && _state == TransactionState.COMMITTING)
            ) {
                "Cannot rollback transaction while transaction is $_state"
            }

            check(children.isEmpty()) {
                "Cannot rollback transaction with active children"
            }

            commands.clear()
            parent?.removeChildLocked(this)
            leases += entityLeases
            loads += entityLoads

            _state = TransactionState.ROLLED_BACK
        }

        abortLoads(loads, cause)
        destroyLoads(loads, cause)
        releaseLeases(leases, cause)
    }

    private fun setCommitState(state: TransactionState) = synchronized(tree.lock) {
        check(_state == TransactionState.COMMITTING) {
            "Transaction state changed while committing: $_state"
        }
        _state = state
    }

    private fun abortLoads(loads: Iterable<PendingEntityLoad>, failure: Throwable) {
        loads.forEach { load ->
            try {
                coordinator.abortLoadReservation(load.reservation, failure)
            } catch (abortFailure: Throwable) {
                failure.addCleanupFailure(abortFailure)
            }
        }
    }

    private fun destroyLoads(loads: Iterable<PendingEntityLoad>, failure: Throwable) {
        val snapshot = loads.toList()
        try {
            server.execute {
                snapshot.forEach { load ->
                    try {
                        with(world) { load.entity?.destroy() }
                    } catch (destroyFailure: Throwable) {
                        failure.addCleanupFailure(destroyFailure)
                    }
                }
            }
        } catch (scheduleFailure: Throwable) {
            failure.addCleanupFailure(scheduleFailure)
        }
    }

    private fun releaseLeases(
        leases: Iterable<EntityLease>,
        initialFailure: Throwable? = null,
    ): Throwable? {
        var failure = initialFailure
        leases.forEach { lease ->
            try {
                coordinator.releaseEntity(lease)
            } catch (releaseFailure: Throwable) {
                failure = failure?.also {
                    it.addCleanupFailure(releaseFailure)
                } ?: releaseFailure
            }
        }
        return failure
    }

    private fun removeChildLocked(child: TransactionContext) {
        check(Thread.holdsLock(tree.lock))

        children -= child

        if (children.isEmpty()) {
            childrenFinished.complete(Unit)
        }
    }

    private fun checkActive() {
        check(_state == TransactionState.ACTIVE) {
            "Cannot perform this action while transaction is $_state"
        }
    }

    private fun Throwable.addCleanupFailure(failure: Throwable) {
        if (failure !== this) {
            addSuppressed(failure)
        }
    }
}

fun EngineServer.createTransactionContext(world: World) = TransactionContext(world, this)

suspend inline fun <T> TransactionContext.withChild(
    block: suspend (childContext: TransactionContext) -> T
): T {
    val child = child()
    return child.rollbackOnFailure(block)
}

suspend inline fun <T> TransactionContext.rollbackOnFailure(
    block: suspend (context: TransactionContext) -> T
): T {
    return try {
        block(this).also {
            this.commit()
        }
    } catch (e: Throwable) {
        when (state) {
            TransactionState.ACTIVE,
            TransactionState.CLOSING -> try {
                rollback(e)
            } catch (rollbackFailure: Throwable) {
                e.addSuppressed(rollbackFailure)
            }

            TransactionState.ROLLED_BACK,
            TransactionState.COMMITTING,
            TransactionState.COMMIT_FAILED -> Unit

            else -> error("Cannot rollback transaction while transaction is $state")
        }
        throw e
    }
}
