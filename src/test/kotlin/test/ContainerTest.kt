package org.lain.engine.test

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.container.AssignedSlot
import org.lain.engine.container.ContainedIn
import org.lain.engine.container.ContainerEntity
import org.lain.engine.container.Entries
import org.lain.engine.container.OccupiedSlots
import org.lain.engine.container.SlotId
import org.lain.engine.container.createContainer
import org.lain.engine.container.createSlotContainer
import org.lain.engine.container.getContainerItems
import org.lain.engine.container.transferOperation
import org.lain.engine.container.tickContainerOperationsSystem
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemStorage
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
//class ContainerTest : EngineTest() {
//    private lateinit var world: World
//
//    @BeforeTest
//    fun setup() {
//        world = World(
//            WorldId("container-test"),
//            namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage()),
//            itemStorage = ItemStorage(),
//            thread = Thread.currentThread(),
//            scriptEngine = ScriptEngine.Dummy,
//        )
//    }
//
//    @Test
//    fun movesItemBetweenOrdinaryContainers() = with(world) {
//        val first = createContainer(testLocation())
//        val second = createContainer(testLocation())
//        val item = addEntity()
//
//        move(first, item)
//        assertEquals(setOf(item), first.getContainerItems())
//        assertEquals(first.entity, item.requireComponent<ContainedIn>().container)
//
//        move(second, item)
//        assertTrue(first.getContainerItems().isEmpty())
//        assertEquals(setOf(item), second.getContainerItems())
//        assertEquals(second.entity, item.requireComponent<ContainedIn>().container)
//    }
//
//    @Test
//    fun movesItemBetweenOrdinaryAndSlottedContainers() = with(world) {
//        val ordinary = createContainer(testLocation())
//        val slotA = SlotId("a")
//        val slotB = SlotId("b")
//        val slotted = createSlotContainer(testLocation(), setOf(slotA, slotB))
//        val item = addEntity()
//
//        move(ordinary, item)
//        move(slotted, item, slotA)
//
//        assertTrue(ordinary.getContainerItems().isEmpty())
//        assertEquals(mapOf(slotA to item), slotted.entity.requireComponent<OccupiedSlots>().slots)
//        assertEquals(setOf(item), slotted.entity.requireComponent<Entries>().items)
//        assertEquals(slotA, item.requireComponent<AssignedSlot>().slot)
//        assertEquals(slotted.entity, item.requireComponent<ContainedIn>().container)
//
//        move(slotted, item, slotB)
//
//        assertEquals(mapOf(slotB to item), slotted.entity.requireComponent<OccupiedSlots>().slots)
//        assertEquals(setOf(item), slotted.entity.requireComponent<Entries>().items)
//        assertEquals(slotB, item.requireComponent<AssignedSlot>().slot)
//
//        move(ordinary, item)
//
//        assertTrue(slotted.entity.requireComponent<OccupiedSlots>().slots.isEmpty())
//        assertTrue(slotted.getContainerItems().isEmpty())
//        assertEquals(setOf(item), ordinary.getContainerItems())
//        assertNull(item.getComponent<AssignedSlot>())
//        assertEquals(ordinary.entity, item.requireComponent<ContainedIn>().container)
//    }
//
//    @Test
//    fun invalidTargetDoesNotPartiallyMoveItem() = with(world) {
//        val source = createContainer(testLocation())
//        val slot = SlotId("only")
//        val target = createSlotContainer(testLocation(), setOf(slot))
//        val item = addEntity()
//        val occupant = addEntity()
//
//        move(source, item)
//        move(target, occupant, slot)
//
//        target.transferOperation(item, slot)
//        assertFailsWith<IllegalArgumentException> {
//            tickContainerOperationsSystem()
//        }
//        clearEvents()
//
//        assertEquals(setOf(item), source.getContainerItems())
//        assertEquals(source.entity, item.requireComponent<ContainedIn>().container)
//        assertEquals(mapOf(slot to occupant), target.entity.requireComponent<OccupiedSlots>().slots)
//        assertEquals(setOf(occupant), target.getContainerItems())
//    }
//
//    @Test
//    fun slotContainerInitialItemsStartConsistent() = with(world) {
//        val slot = SlotId("initial")
//        val item = addEntity()
//        val container = createSlotContainer(testLocation(), setOf(slot), mapOf(slot to item))
//
//        assertEquals(mapOf(slot to item), container.entity.requireComponent<OccupiedSlots>().slots)
//        assertEquals(setOf(item), container.getContainerItems())
//        assertEquals(container.entity, item.requireComponent<ContainedIn>().container)
//        assertEquals(slot, item.requireComponent<AssignedSlot>().slot)
//    }
//
//    context(world: World)
//    private fun move(container: ContainerEntity, item: EngineItem, slot: SlotId? = null) {
//        container.transferOperation(item, slot)
//        world.tickContainerOperationsSystem()
//        world.clearEvents()
//    }
//
//    private fun testLocation() = Location(Vec3(0f))
//}
