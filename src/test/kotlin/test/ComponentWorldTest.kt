package org.lain.engine.test

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentCollisionException
import org.lain.engine.util.ecs.ComponentArray
import org.lain.engine.util.ecs.ComponentMeta
import org.lain.engine.util.ecs.ComponentState
import org.lain.engine.util.ecs.ComponentWorld
import org.lain.engine.util.ecs.EntityCommandBuffer
import org.lain.engine.util.ecs.EntityId
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
import org.lain.engine.server.replication.Changes
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.persistentId
import org.lain.engine.listKotlinComponentTypeEntries
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.util.ecs.castIndexed

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
        assertEquals(TestPosition(7), state.removeComponent(positionType))
        assertNull(state.getComponent(positionType))
    }

    @Test
    fun componentStateCopiesDynamicallyTypedComponents() {
        val source = ComponentState(listOf(TestPosition(7)))
        val target = ComponentState()

        source.copyTo(target)

        assertEquals(TestPosition(7), source.getComponent(positionType))
        assertEquals(TestPosition(7), target.getComponent(positionType))
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
        assertNull(componentWorld.getComponent(entity, positionType))
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
    fun entityDestroyedListenerReceivesPersistentIdAfterDestruction() {
        val entity = componentWorld.addEntity()
        val persistentId = persistentId("destroyed-entity")
        var notification: Pair<EntityId, PersistentId?>? = null
        componentWorld.setComponentWithType(
            entity,
            PersistentIdComponent(persistentId),
            componentTypeOf(PersistentIdComponent::class),
        )
        componentWorld.entityDestroyedListener = { destroyedEntity, destroyedPersistentId ->
            assertFalse(componentWorld.exists(destroyedEntity))
            notification = destroyedEntity to destroyedPersistentId
        }

        componentWorld.destroy(entity)

        assertEquals(entity to persistentId, notification)
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

        assertEquals(mapOf(nameType to TestName("saved")), componentWorld.getSavableComponents(entity))
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
    fun iterationMatchesComponentsByEntityForEverySmallestArray() {
        for (componentCount in 2..5) {
            for (smallestArray in 0 until componentCount) {
                val matching = populateIterationWorld(componentCount, smallestArray).take(2)
                val expected = matching.map { entity ->
                    entity to iterationComponents(entity).take(componentCount)
                }

                assertEquals(
                    expected,
                    collectIteration(componentCount).sortedBy { it.first },
                    "componentCount=$componentCount, smallestArray=$smallestArray",
                )
            }
        }
    }

    @Test
    fun iterationUsesFirstArrayInReverseWhenSizesAreEqual() {
        for (componentCount in 2..5) {
            val matching = populateIterationWorld(componentCount, smallestArray = null).take(2)
            val expected = matching.reversed().map { entity ->
                entity to iterationComponents(entity).take(componentCount)
            }

            assertEquals(expected, collectIteration(componentCount), "componentCount=$componentCount")
        }
    }

    @Test
    fun iterationSkipsQueriesWithAnyEmptyArray() {
        for (componentCount in 2..5) {
            for (emptyArray in 0 until componentCount) {
                val entities = populateIterationWorld(componentCount, emptyArray)
                for (entity in entities) {
                    componentWorld.removeComponent(entity, iterationTypes[emptyArray])
                }

                assertTrue(
                    collectIteration(componentCount).isEmpty(),
                    "componentCount=$componentCount, emptyArray=$emptyArray",
                )
            }
        }
    }

    @Test
    fun iterationAllowsDestroyingCurrentEntityForEverySmallestArray() {
        for (componentCount in 2..5) {
            for (smallestArray in 0 until componentCount) {
                val matching = populateIterationWorld(componentCount, smallestArray).take(2)
                val expected = collectIteration(componentCount)

                assertEquals(
                    expected,
                    collectIteration(componentCount, destroyVisited = true),
                    "componentCount=$componentCount, smallestArray=$smallestArray",
                )
                matching.forEach { assertFalse(componentWorld.exists(it)) }
            }
        }
    }

    private fun populateIterationWorld(componentCount: Int, smallestArray: Int?): List<EntityId> {
        componentWorld = TestComponentWorld()
        componentWorld.registerComponentArrays(testEntries + listKotlinComponentTypeEntries())
        val entities = List(componentCount + 3) { componentWorld.addEntity() }
        for (componentIndex in 0 until componentCount) {
            val order = mutableListOf(entities[0], entities[componentIndex + 2], entities[1])
            if (smallestArray != null && componentIndex != smallestArray) order += entities.last()
            val rotation = componentIndex % order.size
            for (entity in order.drop(rotation) + order.take(rotation)) {
                when (componentIndex) {
                    0 -> componentWorld.setComponentWithType(entity, TestPosition(entity), positionType)
                    1 -> componentWorld.setComponentWithType(entity, TestVelocity(entity * 10), velocityType)
                    2 -> componentWorld.setComponentWithType(entity, TestName("entity-$entity"), nameType)
                    3 -> componentWorld.setComponentWithType(entity, TestFourth(entity * 100), fourthType)
                    4 -> componentWorld.setComponentWithType(entity, TestFifth(entity * 1000), fifthType)
                }
            }
        }
        return entities
    }

    private fun iterationComponents(entity: EntityId): List<Component> = listOf(
        TestPosition(entity),
        TestVelocity(entity * 10),
        TestName("entity-$entity"),
        TestFourth(entity * 100),
        TestFifth(entity * 1000),
    )

    private fun collectIteration(
        componentCount: Int,
        destroyVisited: Boolean = false,
    ): List<Pair<EntityId, List<Component>>> {
        val visited = mutableListOf<Pair<EntityId, List<Component>>>()
        fun record(entity: EntityId, vararg components: Component) {
            visited += entity to components.toList()
            if (destroyVisited) componentWorld.destroy(entity)
        }
        when (componentCount) {
            2 -> componentWorld.iterate2(positionType, velocityType) { entity, a, b ->
                record(entity, a, b)
            }
            3 -> componentWorld.iterate3(positionType, velocityType, nameType) { entity, a, b, c ->
                record(entity, a, b, c)
            }
            4 -> componentWorld.iterate4(positionType, velocityType, nameType, fourthType) { entity, a, b, c, d ->
                record(entity, a, b, c, d)
            }
            5 -> componentWorld.iterate5(positionType, velocityType, nameType, fourthType, fifthType) { entity, a, b, c, d, e ->
                record(entity, a, b, c, d, e)
            }
            else -> error("Unsupported component count: $componentCount")
        }
        return visited
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

        buffer.apply()

        assertEquals(TestPosition(42), componentWorld.getComponent(entity, positionType))
        assertTrue(buffer.isEmpty())
    }

    private data class TestPosition(val x: Int) : Component
    private data class TestVelocity(val x: Int) : Component
    private data class TestName(val value: String) : Component
    private data class TestFourth(val value: Int) : Component
    private data class TestFifth(val value: Int) : Component

    private companion object {
        val positionType = componentTypeOf(TestPosition::class).castIndexed()
        val velocityType = componentTypeOf(TestVelocity::class).castIndexed()
        val nameType = componentTypeOf(TestName::class).castIndexed()
        val fourthType = componentTypeOf(TestFourth::class).castIndexed()
        val fifthType = componentTypeOf(TestFifth::class).castIndexed()
        val iterationTypes = listOf(positionType, velocityType, nameType, fourthType, fifthType)

        val basicMeta = ComponentMeta(savable = false, networking = false)
        val networkingMeta = ComponentMeta(savable = false, networking = true)
        val savableMeta = ComponentMeta(savable = true, networking = false)

        val testEntries = listOf(
            positionType to basicMeta,
            velocityType to networkingMeta,
            nameType to savableMeta,
            fourthType to basicMeta,
            fifthType to basicMeta,
        )
    }
}
