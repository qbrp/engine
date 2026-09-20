package org.lain.engine.data

import org.lain.engine.util.ecs.EntityCommandBuffer
import org.lain.engine.world.World

import kotlinx.coroutines.CompletableDeferred
import org.lain.cyberia.ecs.destroy
import org.lain.engine.server.EngineServer
import org.lain.engine.util.ecs.EntityId
import kotlin.collections.plusAssign

enum class TransactionState {
    ACTIVE,
    CLOSING,
    COMMITTED,
    MERGED,
    ROLLED_BACK,
}

internal class TransactionTree {
    val lock = Any()
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

    class PendingEntityLoad internal constructor(
        val reservation: EntityLoadReservation,
    ) {
        var entity: EntityId? = null
            private set

        fun bind(entity: EntityId) {
            check(this.entity == null)
            this.entity = entity
        }
    }

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
            coordinator.abortLoadReservation(reservation, e)
            throw e
        }
    }

    fun registerEntityLease(lease: EntityLease) = synchronized(tree.lock) {
        checkActive()
        entityLeases += lease
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

            loads += entityLoads.map { load ->
                val entity = checkNotNull(load.entity) {
                    "Cannot commit transaction with unfinished entity load " +
                            load.reservation.uuid
                }

                load to entity
            }
            leases += entityLeases

            _state = TransactionState.COMMITTED
        }

        commands.apply()
        loads.forEach { (load, entity) -> coordinator.completeLoadReservation(load.reservation, entity) }
        leases.forEach { coordinator.releaseEntity(it) }
    }

    fun rollback(cause: Throwable) {
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
                        _state == TransactionState.CLOSING
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

        leases.forEach { coordinator.releaseEntity(it) }
        loads.forEach { coordinator.abortLoadReservation(it.reservation, cause) }
        server.execute {
            loads.forEach {
                with(world) { it.entity?.destroy() }
            }
        }
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
        this.rollback(e)
        throw e
    }
}