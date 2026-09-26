package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.AcquireResult
import org.lain.engine.data.EntityCoordinator
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.persistentId
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

class EntityCoordinatorTest : EngineTest() {
    private lateinit var world: World
    private lateinit var coordinator: EntityCoordinator

    @BeforeEach
    fun setup() {
        val simulation = TestEngineSimulation()
        world = World(WorldId("test"), simulation)
        simulation.loadWorld(world)
        coordinator = EntityCoordinator()
    }

    @Test
    fun acquireReloadsDestroyedEntityWhoseIdWasReused() {
        val originalPersistentId = persistentId("original")
        val originalEntity = world.addEntity {
            setComponent(PersistentIdComponent(originalPersistentId))
        }
        coordinator.registerEntity(world, originalPersistentId, originalEntity)

        world.destroy(originalEntity)
        val replacementEntity = world.addEntity {
            setComponent(PersistentIdComponent(persistentId("replacement")))
        }

        assertEquals(originalEntity, replacementEntity)
        assertInstanceOf(
            AcquireResult.Acquired::class.java,
            coordinator.acquire(world, originalPersistentId),
        )
    }
}
