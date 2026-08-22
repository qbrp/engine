package org.lain.engine.test

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentCollisionException
import org.lain.engine.item.ItemStorage
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.listKotlinComponentTypeEntries
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ScriptEngine
import org.lain.engine.server.Changes
import org.lain.engine.util.component.castIndexed
import java.util.concurrent.ConcurrentHashMap

class ComponentWorldTest : EngineTest() {
    private lateinit var componentWorld: ComponentWorld

    @BeforeEach
    fun setup() {
        componentWorld = TestComponentWorld()
        componentWorld.registerComponentArrays(testEntries + listKotlinComponentTypeEntries())
    }

    @Test
    fun componentArrayKeepsDenseStorageConsistentWhenRemovingMiddleElement() {
        val array = ComponentArray(0, basicMeta, positionType)

        array.setComponent(10, TestPosition(10))
        array.setComponent(20, TestPosition(20))
        array.setComponent(30, TestPosition(30))

        assertEquals(TestPosition(20), array.removeComponent(20))

        assertEquals(listOf(TestPosition(10), TestPosition(30)), array.components)
        assertEquals(10, array.entityOf(0))
        assertEquals(30, array.entityOf(1))
        assertNull(array.componentOf(20))
        assertEquals(TestPosition(30), array.componentOf(30))
    }

    @Test
    fun componentStateRejectsTwoComponentsOfSameType() {
        val state = ComponentState()
        val position = TestPosition(1)

        assertSame(position, state.setComponent(positionType, position))

        assertThrows<ComponentCollisionException> {
            state.setComponent(positionType, TestPosition(2))
        }
    }

    @Test
    fun componentStateIndexesComponentsByTypeAndName() {
        val state = ComponentState()

        state.setComponent(positionType, TestPosition(7))

        assertEquals(TestPosition(7), state.getComponent(positionType))
        assertEquals(TestPosition(7), state.getComponent<TestPosition>(positionType.id))
        assertEquals(TestPosition(7), state.removeComponent(positionType))
        assertNull(state.getComponent(positionType))
        assertNull(state.getComponent<TestPosition>(positionType.id))
    }

    @Test
    fun componentWorldAddsReadsRemovesAndDestroysComponents() {
        val entity = componentWorld.addEntity()

        componentWorld.setComponentWithType(entity, TestPosition(3), positionType)

        assertTrue(componentWorld.exists(entity))
        assertTrue(componentWorld.hasComponent(entity, positionType))
        assertEquals(TestPosition(3), componentWorld.getComponent(entity, positionType))
        assertEquals(TestPosition(3), componentWorld.removeComponent(entity, positionType))
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

        componentWorld.setComponentWithType(entity, TestVelocity(4), velocityType)

        assertEquals(listOf(TestVelocity(4)), componentWorld.getNetworkedComponents(entity))
        val changes = componentWorld.getComponent(entity, componentTypeOf(Changes::class))!!
        assertTrue(changes.updated[velocityType.idx])

        changes.clear()

        assertFalse(changes.changed)
        assertTrue(changes.updated.isEmpty)
    }

    @Test
    fun nonNetworkedComponentsAreNotMarkedDirty() {
        val entity = componentWorld.addEntity()

        componentWorld.setComponentWithType(entity, TestPosition(9), positionType)
        componentWorld.markDirty(entity, positionType)

        assertNull(componentWorld.getComponent(entity, componentTypeOf(Changes::class)))
    }

    @Test
    fun removingNetworkedComponentIsTracked() {
        val entity = componentWorld.addEntity()
        componentWorld.setComponentWithType(entity, TestVelocity(4), velocityType)
        val changes = componentWorld.getComponent(entity, componentTypeOf(Changes::class))!!
        changes.clear()

        componentWorld.removeComponent(entity, velocityType)

        assertTrue(changes.changed)
        assertTrue(changes.removed[velocityType.idx])
        assertFalse(changes.updated[velocityType.idx])
    }

    @Test
    fun networkedComponentChangesNotifyListener() {
        val entity = componentWorld.addEntity()
        val notifications = mutableListOf<Pair<EntityId, String>>()
        componentWorld.networkedComponentChangeListener = { changedEntity, type ->
            notifications += changedEntity to type.id
        }

        componentWorld.setComponentWithType(entity, TestVelocity(4), velocityType)
        componentWorld.markDirty(entity, velocityType)
        componentWorld.removeComponent(entity, velocityType)

        assertEquals(
            listOf(
                entity to velocityType.id,
                entity to velocityType.id,
                entity to velocityType.id,
            ),
            notifications,
        )

        componentWorld.setComponentWithType(entity, TestVelocity(5), velocityType)
        notifications.clear()
        componentWorld.destroy(entity)

        assertEquals(listOf(entity to velocityType.id), notifications)
    }

    @Test
    fun savableComponentsAreFilteredByMetadata() {
        val entity = componentWorld.addEntity()

        componentWorld.setComponentWithType(entity, TestPosition(1), positionType)
        componentWorld.setComponentWithType(entity, TestName("saved"), nameType)
        componentWorld.setComponentWithType(entity, TestVelocity(2), velocityType)

        assertEquals(listOf(TestName("saved")), componentWorld.getSavableComponents(entity))
    }

    @Test
    fun collectRequiresAllFiltersAndCopiesSelectedComponents() {
        val matching = componentWorld.addEntity()
        val missingVelocity = componentWorld.addEntity()

        componentWorld.setComponentWithType(matching, TestPosition(1), positionType)
        componentWorld.setComponentWithType(matching, TestVelocity(2), velocityType)
        componentWorld.setComponentWithType(matching, TestName("kept"), nameType)
        componentWorld.setComponentWithType(missingVelocity, TestPosition(3), positionType)
        componentWorld.setComponentWithType(missingVelocity, TestName("ignored"), nameType)

        val collected = componentWorld.collect(listOf(positionType, velocityType)) { array ->
            array.type == positionType || array.type == nameType
        }

        assertEquals(1, collected.size)
        val (entity, state) = collected.single()
        assertEquals(matching, entity)
        assertEquals(TestPosition(1), state.getComponent(positionType))
        assertEquals(TestName("kept"), state.getComponent(nameType))
        assertNull(state.getComponent(velocityType))
    }

    @Test
    fun iterationOnlyVisitsEntitiesWithAllRequestedComponents() {
        val matchingA = componentWorld.addEntity()
        val matchingB = componentWorld.addEntity()
        val missingVelocity = componentWorld.addEntity()

        componentWorld.setComponentWithType(matchingA, TestPosition(1), positionType)
        componentWorld.setComponentWithType(matchingA, TestVelocity(10), velocityType)
        componentWorld.setComponentWithType(matchingB, TestPosition(2), positionType)
        componentWorld.setComponentWithType(matchingB, TestVelocity(20), velocityType)
        componentWorld.setComponentWithType(missingVelocity, TestPosition(3), positionType)

        val visited = mutableMapOf<EntityId, Pair<TestPosition, TestVelocity>>()
        componentWorld.iterate2(positionType, velocityType) { entity, position, velocity ->
            visited[entity] = position to velocity
        }

        assertEquals(
            mapOf(
                matchingA to (TestPosition(1) to TestVelocity(10)),
                matchingB to (TestPosition(2) to TestVelocity(20))
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
                componentWorld.setComponentWithType(entity, TestPosition(1), positionType)
            }
        }

        thread.start()
        thread.join()

        assertInstanceOf(IllegalStateException::class.java, failure)
    }

    @Test
    fun entityCommandBufferDefersMutationsUntilApplied() {
        val buffer = EntityCommandBuffer(componentWorld)

        val entity = buffer.addEntity()
        buffer.setComponentWithType(entity, TestPosition(42), positionType)

        assertTrue(componentWorld.exists(entity))
        assertFalse(componentWorld.hasComponent(entity, positionType))

        buffer.apply(componentWorld)

        assertEquals(TestPosition(42), componentWorld.getComponent(entity, positionType))
        assertTrue(buffer.isEmpty())
    }

    private data class TestPosition(val x: Int) : Component
    private data class TestVelocity(val x: Int) : Component
    private data class TestName(val value: String) : Component

    private companion object {
        val positionType = componentTypeOf(TestPosition::class).castIndexed()
        val velocityType = componentTypeOf(TestVelocity::class).castIndexed()
        val nameType = componentTypeOf(TestName::class).castIndexed()

        val basicMeta = ComponentMeta(savable = false, serializationClass = null, networking = false)
        val networkingMeta = ComponentMeta(savable = false, serializationClass = null, networking = true)
        val savableMeta = ComponentMeta(savable = true, serializationClass = TestName::class, networking = false)

        val testEntries = listOf(
            positionType to basicMeta,
            velocityType to networkingMeta,
            nameType to savableMeta
        )
    }
}
