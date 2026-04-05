package org.lain.engine.test

import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.container.*
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.instantiateItem
import org.lain.engine.mc.updatePlayerOwnedItems
import org.lain.engine.world.World
import kotlin.test.Test

class ContainerTest : EngineTest() {
    @Test
    fun testContainerOperations() = with(DummyWorld()) {
        val world = this
        val itemStorage = ItemStorage()
        val containerA = world.createContainer(world.dummyLocation())
        val containerB = world.createContainer(world.dummyLocation())
        val item = instantiateItem(world, DummyItemPrefab(), itemStorage)

        containerA.setComponent(AssignItem(item.uuid))
        world.updateContainers(itemStorage)

        containerB.setComponent(AssignItem(item.uuid))
        world.updateContainers(itemStorage)

        containerA.setComponent(AssignItem(item.uuid))
        world.updateContainers(itemStorage)

        containerA.assertContained(item)
        containerB.assertNotContained(item)
        item.assertHasContainedComponent(containerA)
        assert(!containerA.hasComponent<AssignItem>()) { "Компонент AssignItem не удален" }
        assert(!containerB.hasComponent<DetachItem>()) { "Компонент DetachItem не удален" }
    }

    @Test
    fun testSlotContainerOperations() = with(DummyWorld()) {
        val world = this
        val itemStorage = ItemStorage()
        val slot1 = SlotId("slot1")
        val slot2 = SlotId("slot2")
        val slots = setOf(slot1, slot2)
        val containerA = world.createSlotContainer(world.dummyLocation(), slots)
        val containerB = world.createSlotContainer(world.dummyLocation(), slots)
        val item = instantiateItem(world, DummyItemPrefab(), itemStorage)

        containerA.setComponent(AssignSlot(item, slot1))
        world.updateContainers(itemStorage)

        containerB.setComponent(AssignSlot(item, slot1))
        world.updateContainers(itemStorage)

        containerA.setComponent(AssignSlot(item, slot1))
        world.updateContainers(itemStorage)

        containerA.assertContained(item)
        containerB.assertNotContained(item)
        item.assertHasContainedComponent(containerA)
        assert(!containerA.hasComponent<AssignItem>()) { "Компонент AssignItem не удален" }
        assert(!containerB.hasComponent<DetachItem>()) { "Компонент DetachItem не удален" }
    }

    context(world: World)
    private fun EntityId.assertContained(item: EngineItem) {
        assert(world.getContainerItems(this).contains(item)) { "Предмет не был добавлен в контейнер" }
    }

    context(world: World)
    private fun EntityId.assertNotContained(item: EngineItem) {
        assert(!world.getContainerItems(this).contains(item)) { "Предмет был добавлен в контейнер" }
    }

    context(world: World)
    private fun EngineItem.assertHasContainedComponent(container: EntityId) {
        val containedIn = entity.getComponent<ContainedIn>()
        assert(containedIn != null && containedIn.container == container) { "Компонент хранения предмета не найден" }
    }

    private fun World.updateContainers(itemStorage: ItemStorage) {
        updateContainerSystems(this, itemStorage)
        players.forEach { player -> updatePlayerOwnedItems(this, player) }
        postUpdateContainerSystems(this)
    }
}