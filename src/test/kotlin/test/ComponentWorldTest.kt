package org.lain.engine.test

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentCollisionException
import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.item.ItemStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.emptyNamespacedStorage
import org.lain.engine.util.Storage
import org.lain.engine.util.component.ComponentArray
import org.lain.engine.util.component.ComponentMeta
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.ComponentWorld
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.lain.engine.util.component.EngineComponentType
import org.lain.engine.util.component.IndexedComponentType
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.BeforeTest
import kotlin.test.Test

class ComponentWorldTest : EngineTest() {
    private lateinit var componentWorld: ComponentWorld

    @BeforeTest
    fun setup() {
        componentWorld = ComponentWorld(
            Thread.currentThread(),
            ConcurrentHashMap(),
            Storage()
        )
        componentWorld.registerComponentArrays(testEntries)
    }

    @Test
    fun componentArrayKeepsDenseStorageConsistentWhenRemovingMiddleElement() {
        val array = ComponentArray(0, basicMeta, positionType)

        array.setComponent(10, Position(10))
        array.setComponent(20, Position(20))
        array.setComponent(30, Position(30))

        assertEquals(Position(20), array.removeComponent(20))

        assertEquals(listOf(Position(10), Position(30)), array.components)
        assertEquals(10, array.entityOf(0))
        assertEquals(30, array.entityOf(1))
        assertNull(array.componentOf(20))
        assertEquals(Position(30), array.componentOf(30))
    }

    @Test
    fun componentStateRejectsTwoComponentsOfSameType() {
        val state = ComponentState()
        val position = Position(1)

        assertSame(position, state.setComponent(positionType, position))

        assertThrows<ComponentCollisionException> {
            state.setComponent(positionType, Position(2))
        }
    }

    @Test
    fun componentStateIndexesComponentsByTypeAndName() {
        val state = ComponentState()

        state.setComponent(positionType, Position(7))

        assertEquals(Position(7), state.getComponent(positionType))
        assertEquals(Position(7), state.getComponent<Position>("test_position"))
        assertEquals(Position(7), state.removeComponent(positionType))
        assertNull(state.getComponent(positionType))
        assertNull(state.getComponent<Position>("test_position"))
    }

    @Test
    fun componentWorldAddsReadsRemovesAndDestroysComponents() {
        val entity = componentWorld.addEntity()

        componentWorld.setComponentWithType(entity, Position(3), positionType)

        assertTrue(componentWorld.exists(entity))
        assertTrue(componentWorld.hasComponent(entity, positionType))
        assertEquals(Position(3), componentWorld.getComponent(entity, positionType))
        assertEquals(Position(3), componentWorld.removeComponent(entity, positionType))
        assertFalse(componentWorld.hasComponent(entity, positionType))

        componentWorld.destroy(entity)

        assertFalse(componentWorld.exists(entity))
        assertThrows<IllegalArgumentException> {
            componentWorld.getComponent(entity, positionType)
        }
    }

    @Test
    fun componentWorldReusesDestroyedEntityIds() {
        val first = componentWorld.addEntity()
        val second = componentWorld.addEntity()

        componentWorld.destroy(first)

        assertEquals(first, componentWorld.addEntity())
        assertTrue(componentWorld.exists(first))
        assertTrue(componentWorld.exists(second))
    }

    @Test
    fun networkingComponentsAreTrackedAsDirtyUntilCleared() {
        val entity = componentWorld.addEntity()

        componentWorld.setComponentWithType(entity, Velocity(4), velocityType)

        assertEquals(listOf(Velocity(4)), componentWorld.getNetworkedComponents(entity))
        assertEquals(listOf(Velocity(4)), componentWorld.getDirtyNetworkedComponents(entity))

        componentWorld.clearDirtyComponents(entity)

        assertTrue(componentWorld.getDirtyNetworkedComponents(entity).isEmpty())
    }

    @Test
    fun nonNetworkedComponentsAreNotMarkedDirty() {
        val entity = componentWorld.addEntity()

        componentWorld.setComponentWithType(entity, Position(9), positionType)
        componentWorld.markDirty(entity, positionType)

        assertTrue(componentWorld.getDirtyNetworkedComponents(entity).isEmpty())
    }

    @Test
    fun savableComponentsAreFilteredByMetadata() {
        val entity = componentWorld.addEntity()

        componentWorld.setComponentWithType(entity, Position(1), positionType)
        componentWorld.setComponentWithType(entity, Name("saved"), nameType)
        componentWorld.setComponentWithType(entity, Velocity(2), velocityType)

        assertEquals(listOf(Name("saved")), componentWorld.getSavableComponents(entity))
    }

    @Test
    fun collectRequiresAllFiltersAndCopiesSelectedComponents() {
        val matching = componentWorld.addEntity()
        val missingVelocity = componentWorld.addEntity()

        componentWorld.setComponentWithType(matching, Position(1), positionType)
        componentWorld.setComponentWithType(matching, Velocity(2), velocityType)
        componentWorld.setComponentWithType(matching, Name("kept"), nameType)
        componentWorld.setComponentWithType(missingVelocity, Position(3), positionType)
        componentWorld.setComponentWithType(missingVelocity, Name("ignored"), nameType)

        val collected = componentWorld.collect(listOf(positionType, velocityType)) { array ->
            array.type == positionType || array.type == nameType
        }

        assertEquals(1, collected.size)
        val (entity, state) = collected.single()
        assertEquals(matching, entity)
        assertEquals(Position(1), state.getComponent(positionType))
        assertEquals(Name("kept"), state.getComponent(nameType))
        assertNull(state.getComponent(velocityType))
    }

    @Test
    fun iterationOnlyVisitsEntitiesWithAllRequestedComponents() {
        val matchingA = componentWorld.addEntity()
        val matchingB = componentWorld.addEntity()
        val missingVelocity = componentWorld.addEntity()

        componentWorld.setComponentWithType(matchingA, Position(1), positionType)
        componentWorld.setComponentWithType(matchingA, Velocity(10), velocityType)
        componentWorld.setComponentWithType(matchingB, Position(2), positionType)
        componentWorld.setComponentWithType(matchingB, Velocity(20), velocityType)
        componentWorld.setComponentWithType(missingVelocity, Position(3), positionType)

        val visited = mutableMapOf<EntityId, Pair<Position, Velocity>>()
        componentWorld.iterate2(positionType, velocityType) { entity, position, velocity ->
            visited[entity] = position to velocity
        }

        assertEquals(
            mapOf(
                matchingA to (Position(1) to Velocity(10)),
                matchingB to (Position(2) to Velocity(20))
            ),
            visited
        )
    }

    @Test
    fun componentWorldRejectsComponentOperationsFromAnotherThread() {
        val entity = componentWorld.addEntity()
        var failure: Throwable? = null

        val thread = Thread {
            failure = assertThrows<IllegalStateException> {
                componentWorld.setComponentWithType(entity, Position(1), positionType)
            }
        }

        thread.start()
        thread.join()

        assertInstanceOf(IllegalStateException::class.java, failure)
    }

    @Test
    fun entityCommandBufferDefersMutationsUntilApplied() {
        val world = testWorld()
        val buffer = EntityCommandBuffer(world)

        val entity = buffer.addEntity()
        buffer.setComponentWithType(entity, Position(42), positionType)

        assertTrue(world.exists(entity))
        assertFalse(world.hasComponent(entity, positionType))

        buffer.apply(world)

        assertEquals(Position(42), world.getComponent(entity, positionType))
        assertTrue(buffer.isEmpty())
    }

    private fun testWorld(): World {
        val world = World(
            WorldId("component-test"),
            namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(emptyNamespacedStorage()),
            itemStorage = ItemStorage(),
            thread = Thread.currentThread()
        )
        world.componentManager.registerComponentArrays(testEntries)
        return world
    }

    private data class Position(val x: Int) : Component
    private data class Velocity(val x: Int) : Component
    private data class Name(val value: String) : Component

    private companion object {
        val positionType = EngineComponentType<Position>("test_position")
        val velocityType = EngineComponentType<Velocity>("test_velocity")
        val nameType = EngineComponentType<Name>("test_name")

        val basicMeta = ComponentMeta(savable = false, serializationClass = null, networking = false)
        val networkingMeta = ComponentMeta(savable = false, serializationClass = null, networking = true)
        val savableMeta = ComponentMeta(savable = true, serializationClass = Name::class, networking = false)

        val testEntries = listOf(
            positionType to basicMeta,
            velocityType to networkingMeta,
            nameType to savableMeta
        )
    }
}
