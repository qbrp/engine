package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.persistentId
import org.lain.engine.item.HeldBy
import org.lain.engine.item.Item
import org.lain.engine.item.tickItemOwnershipSystem
import org.lain.engine.item.toItemPrefabId
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Equipment
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.interaction.Hand
import org.lain.engine.player.interaction.HandType
import org.lain.engine.player.randomPlayerId
import org.lain.engine.player.toEquipmentSlotId
import org.lain.engine.script.EngineId
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

class ItemOwnershipTest : EngineTest() {
    private lateinit var world: World

    @BeforeEach
    fun setup() {
        world = World(WorldId("ownership-test"), TestEngineSimulation(isClient = true))
    }

    @Test
    fun simulatesInventoryCursorAndEquipmentOfEligiblePlayer() = with(world) {
        val player = createPlayer()
        val inventory = player.entity.requireComponent<PlayerInventory>()
        val inventoryItem = createItem()
        val cursorItem = createItem()
        val equippedItem = createItem()
        inventory.items += inventoryItem
        inventory.cursorItem = cursorItem
        player.entity.requireComponent<Equipment>().slots[slot] = equippedItem
        val existingLocation = Location(Vec3(1, 2, 3))
        cursorItem.setComponent(existingLocation)

        tickItemOwnershipSystem()

        for (item in listOf(inventoryItem, cursorItem, equippedItem)) {
            assertSame(player, item.requireComponent<HeldBy>().owner)
            val location = item.requireComponent<Location>()
            assertEquals(10f, location.x)
            assertEquals(20f, location.y)
            assertEquals(30f, location.z)
        }
        assertSame(existingLocation, cursorItem.getComponent<Location>())
    }

    @Test
    fun removingItemFromInventoryClearsItsOwner() = with(world) {
        val player = createPlayer()
        val item = createItem()
        val inventory = player.entity.requireComponent<PlayerInventory>()
        inventory.items += item
        tickItemOwnershipSystem()
        assertSame(player, item.requireComponent<HeldBy>().owner)

        inventory.items.remove(item)
        tickItemOwnershipSystem()
        assertNull(item.requireComponent<HeldBy>().owner)
    }

    private fun createPlayer(): EnginePlayer = with(world) {
        val entity = addEntity()
        val player = EnginePlayer(randomPlayerId(), entity, world)
        val mainHand = addEntity()
        val offHand = addEntity()
        mainHand.setComponent(Hand(entity, HandType.MAIN, offHand))
        offHand.setComponent(Hand(entity, HandType.OFFHAND, mainHand))
        entity.setComponent(PlayerComponent(player))
        entity.setComponent(PlayerInventory(mutableSetOf(), mainHand, offHand))
        entity.setComponent(Equipment(mutableMapOf()))
        entity.setComponent(Location(Vec3(10, 20, 30)))
        player
    }

    private fun createItem(): EntityId = with(world) {
        addEntity().also { entity ->
            entity.setComponent(Item(persistentId("item-$entity"), EngineId("test/item").toItemPrefabId()))
        }
    }

    private companion object {
        val slot = EngineId("test/slot").toEquipmentSlotId()
    }
}
