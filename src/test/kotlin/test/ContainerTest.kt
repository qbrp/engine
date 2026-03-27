package org.lain.engine.test

import org.lain.engine.container.*
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.instantiateItem
import org.lain.engine.mc.updatePlayerOwnedItems
import org.lain.engine.util.component.getComponent
import org.lain.engine.util.component.hasComponent
import org.lain.engine.util.component.setComponent
import org.lain.engine.world.World
import kotlin.test.Test

class ContainerTest {
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

        assert(world.getContainerItems(containerA).contains(item)) { "Предмет не был добавлен в контейнер А" }
        assert(world.getContainerItems(containerB).isEmpty()) { "Предмет не должен быть в контейнере Б" }
        val containedIn = item.entity.getComponent<ContainedIn>()
        assert(containedIn != null && containedIn.container == containerA) { "Компонент хранения предмета не найден" }
        assert(!containerA.hasComponent<AssignItem>()) { "Компонент AssignItem не удален" }
        assert(!containerB.hasComponent<DetachItem>()) { "Компонент DetachItem не удален" }
    }

    private fun World.updateContainers(itemStorage: ItemStorage) {
        updateSlotContainers(this)
        updateContainerOperations(this, itemStorage)
        detachSlotContainers(this)
        players.forEach { player -> updatePlayerOwnedItems(this, player) }
        updateContainedPlayerInventoryItems(this)
        clearAssignItemsOperations(this)
    }
}