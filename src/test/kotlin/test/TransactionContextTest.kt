package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.AcquireResult
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.TransactionState
import org.lain.engine.data.asRawEngineId
import org.lain.engine.data.createTransactionContext
import org.lain.engine.data.persistentId
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.ExecutionResult
import org.lain.engine.script.SNil
import org.lain.engine.script.Script
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.EngineServer
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World
import java.nio.file.Path

class TransactionContextTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    private lateinit var server: EngineServer
    private lateinit var world: World

    @BeforeEach
    fun setup() {
        server = setupTestEngineServer(tempDir)
        world = server.simulation.defaultWorld
    }

    @AfterEach
    fun teardown() {
        runBlocking { server.chunkPersistence.close() }
        server.chat.close()
    }

    @Test
    fun commitPublishesLoadsBeforeReleasingLeases() {
        val coordinator = server.entityCoordinator
        val existingId = persistentId("existing")
        val existingEntity = world.addEntity {
            setComponent(PersistentIdComponent(existingId))
        }
        coordinator.registerEntity(world, existingId, existingEntity)

        val transaction = server.createTransactionContext(world)
        val existingLease = assertInstanceOf(
            AcquireResult.Leased::class.java,
            coordinator.acquire(world, existingId),
        )
        transaction.registerEntityLease(existingLease.lease)

        val loadedId = persistentId("loaded")
        val reservation = assertInstanceOf(
            AcquireResult.Acquired::class.java,
            coordinator.acquire(world, loadedId),
        ).reservation
        val pendingLoad = transaction.adoptEntityLoad(reservation)
        val loadedEntity = transaction.commands.addEntity {
            setComponent(PersistentIdComponent(loadedId))
        }
        pendingLoad.bind(loadedEntity, null)

        val loading = assertInstanceOf(
            AcquireResult.Loading::class.java,
            coordinator.acquire(world, loadedId),
        )
        var loadWasPublished = false
        var unloadWhilePublishing: Any? = null
        loading.deferred.invokeOnCompletion {
            loadWasPublished = true
            unloadWhilePublishing = coordinator.tryUnloadEntity(world, existingId)
        }

        transaction.commit()

        assertTrue(loadWasPublished)
        assertNull(unloadWhilePublishing)
        assertSame(TransactionState.COMMITTED, transaction.state)

        val loadedLease = assertInstanceOf(
            AcquireResult.Leased::class.java,
            coordinator.acquire(world, loadedId),
        )
        coordinator.releaseEntity(loadedLease.lease)
        finishUnload(loadedId, loadedEntity)
        finishUnload(existingId, existingEntity)
    }

    @Test
    fun failedCommitAbortsLoadsAndReleasesEveryLease() {
        val coordinator = server.entityCoordinator
        val existingId = persistentId("existing")
        val existingEntity = world.addEntity {
            setComponent(PersistentIdComponent(existingId))
        }
        coordinator.registerEntity(world, existingId, existingEntity)

        val transaction = server.createTransactionContext(world)
        val existingLease = assertInstanceOf(
            AcquireResult.Leased::class.java,
            coordinator.acquire(world, existingId),
        )
        transaction.registerEntityLease(existingLease.lease)

        val loadedId = persistentId("failed-load")
        val reservation = assertInstanceOf(
            AcquireResult.Acquired::class.java,
            coordinator.acquire(world, loadedId),
        ).reservation
        val pendingLoad = transaction.adoptEntityLoad(reservation)
        val loadedEntity = transaction.commands.addEntity {
            setComponent(PersistentIdComponent(loadedId))
        }
        pendingLoad.bind(
            loadedEntity,
            EntityPersistenceData.Item("test/item".asRawEngineId(), 1, 1),
        )

        val expectedFailure = RuntimeException("materialization failed")
        server.simulation.callbacks = Callbacks(
            mapOf<CallbackType<*, *>, Script<*, *>>(
                CallbackType.ENTITY_MATERIALIZATION to object :
                    Script<ScriptContext.EntityMaterialization, SNil> {
                    override fun execute(
                        context: ScriptContext.EntityMaterialization,
                    ): ExecutionResult<SNil> = throw expectedFailure
                }
            )
        )

        val actualFailure = assertThrows(RuntimeException::class.java) {
            transaction.commit()
        }

        assertSame(expectedFailure, actualFailure)
        assertSame(TransactionState.COMMIT_FAILED, transaction.state)
        assertFalse(world.componentManager.exists(loadedEntity))
        assertNull(world.persistentIdToEntity[loadedId])

        val retry = assertInstanceOf(
            AcquireResult.Acquired::class.java,
            coordinator.acquire(world, loadedId),
        )
        coordinator.abortLoadReservation(retry.reservation, RuntimeException("test cleanup"))
        finishUnload(existingId, existingEntity)
    }

    private fun finishUnload(id: PersistentId, entity: EntityId) {
        val unload = server.entityCoordinator.tryUnloadEntity(world, id)
        assertNotNull(unload)
        world.destroy(entity)
        server.entityCoordinator.finishEntityUnload(unload!!)
    }
}
