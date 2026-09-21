package org.lain.engine.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class EntityCoordinator {
    private val states = ConcurrentHashMap<EntityKey, EntityState>()

    fun registerEntity(world: World, uuid: PersistentId, entity: EntityId) {
        states.compute(EntityKey(world.id, uuid)) { _, currentState ->
            check(currentState == null) {
                "Entity $uuid is already coordinated in world ${world.id}"
            }
            EntityState.Loaded(entity)
        }
    }

    fun acquire(
        world: World,
        uuid: PersistentId,
    ): AcquireResult {
        var result: AcquireResult? = null
        states.compute(EntityKey(world.id, uuid)) { key, currentState ->
            when (currentState) {
                null -> {
                    val deferred = CompletableDeferred<EntityLoadResult>()
                    result = AcquireResult.Acquired(
                        EntityLoadReservation(world.id, uuid, deferred)
                    )
                    EntityState.Loading(deferred)
                }

                is EntityState.Loaded -> {
                    result = AcquireResult.Leased(EntityLease(world.id, currentState.entity, uuid))
                    currentState.copy(leases = currentState.leases + 1)
                }

                is EntityState.Loading -> {
                    result = AcquireResult.Loading(currentState.deferred)
                    currentState
                }

                is EntityState.Unloading -> {
                    result = AcquireResult.Unloading(currentState.deferred)
                    currentState
                }
            }
        }
        return result!!
    }

    fun completeLoadReservation(
        reservation: EntityLoadReservation,
        entity: EntityId,
    ) {
        val key = EntityKey(reservation.world, reservation.uuid)

        states.compute(key) { _, state ->
            checkReservation(state, reservation)
            EntityState.Loaded(entity, 0)
        }

        check(
            reservation.deferred.complete(
                EntityLoadResult.Success(entity)
            )
        )
    }

    fun abortLoadReservation(
        reservation: EntityLoadReservation,
        e: Throwable,
    ) {
        val key = EntityKey(reservation.world, reservation.uuid)

        states.compute(key) { _, state ->
            checkReservation(state, reservation)
            null
        }

        check(
            reservation.deferred.complete(
                EntityLoadResult.Failure(e)
            )
        )
    }

    fun releaseEntity(lease: EntityLease) {
        check(lease.released.compareAndSet(false, true)) {
            "Lease has already been released"
        }
        states.compute(
            EntityKey(lease.world, lease.persistentId)
        ) { _, state ->
            check(
                state is EntityState.Loaded && state.entity == lease.entity && state.leases > 0
            )

            state.copy(leases = state.leases - 1)
        }
    }

    fun tryUnloadEntity(world: World, persistentId: PersistentId): EntityUnload? {
        var result: EntityUnload? = null
        states.computeIfPresent(
            EntityKey(world.id, persistentId)
        ) { _, state ->
            if (state is EntityState.Loaded && state.leases == 0) {
                val deferred = CompletableDeferred<Unit>()
                EntityState.Unloading(deferred)
                    .also { result = EntityUnload(world.id, persistentId, deferred) }
            } else {
                state
            }
        }
        return result
    }

    fun finishEntityUnload(unload: EntityUnload) {
        states.computeIfPresent(
            EntityKey(unload.world, unload.uuid)
        ) { _, currentState ->
            check(currentState is EntityState.Unloading && currentState.deferred == unload.deferred) {
                "Cannot unload entity"
            }
            null
        }
        unload.deferred.complete(Unit)
    }

    private fun checkReservation(previousState: EntityState?, reservation: EntityLoadReservation) {
        check(previousState is EntityState.Loading && previousState.deferred == reservation.deferred) {
            "Reservation is no longer active"
        }
    }

    data class EntityKey(
        val world: WorldId,
        val entity: PersistentId,
    )

    sealed interface EntityState {
        data class Loaded(val entity: EntityId, val leases: Int = 0) : EntityState
        data class Loading(val deferred: CompletableDeferred<EntityLoadResult>) : EntityState
        data class Unloading(val deferred: CompletableDeferred<Unit>) : EntityState
    }
}

class EntityLease internal constructor(
    val world: WorldId,
    val entity: EntityId,
    val persistentId: PersistentId,
) {
    internal val released = AtomicBoolean(false)
}

class EntityLoadReservation internal constructor(
    val world: WorldId,
    val uuid: PersistentId,
    internal val deferred: CompletableDeferred<EntityLoadResult>
)

class EntityUnload internal constructor(
    val world: WorldId,
    val uuid: PersistentId,
    val deferred: CompletableDeferred<Unit>
)

sealed class AcquireResult {
    data class Acquired(val reservation: EntityLoadReservation) : AcquireResult()
    data class Loading(val deferred: Deferred<EntityLoadResult>) : AcquireResult()
    data class Leased(val lease: EntityLease) : AcquireResult()
    data class Unloading(val deferred: CompletableDeferred<Unit>) : AcquireResult()
}
