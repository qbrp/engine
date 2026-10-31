package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.EngineSimulation
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
import org.lain.engine.util.ecs.EntityId
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

        tickItemOwnershipSystem(EngineSimulation.SimulationTickExtension.DUMMY)

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
    fun skippedPlayerKeepsStaleInventoryWithoutSimulatingAnyOfItsItems() = with(world) {
        val skippedPlayer = createPlayer()
        val eligiblePlayer = createPlayer()
        val removedItem = createItem()
        val skippedItem = createItem()
        val eligibleItem = createItem()
        val inventory = skippedPlayer.entity.requireComponent<PlayerInventory>()
        val equipment = skippedPlayer.entity.requireComponent<Equipment>()
        inventory.items.addAll(listOf(removedItem, skippedItem))
        inventory.cursorItem = removedItem
        inventory.mainHandItem = removedItem
        inventory.offHandItem = removedItem
        inventory.mainHandInteractor.requireComponent<Hand>().item = removedItem
        inventory.offHandInteractor.requireComponent<Hand>().item = removedItem
        equipment.slots[slot] = removedItem
        eligiblePlayer.entity.requireComponent<PlayerInventory>().items += eligibleItem

        destroy(removedItem)
        tickItemOwnershipSystem(simulationFilter { it === eligiblePlayer })

        assertFalse(exists(removedItem))
        assertEquals(setOf(removedItem, skippedItem), inventory.items)
        assertEquals(removedItem, inventory.cursorItem)
        assertEquals(removedItem, inventory.mainHandItem)
        assertEquals(removedItem, inventory.offHandItem)
        assertEquals(removedItem, inventory.mainHandInteractor.requireComponent<Hand>().item)
        assertEquals(removedItem, inventory.offHandInteractor.requireComponent<Hand>().item)
        assertEquals(mapOf(slot to removedItem), equipment.slots)
        assertNull(skippedItem.getComponent<HeldBy>())
        assertNull(skippedItem.getComponent<Location>())
        assertSame(eligiblePlayer, eligibleItem.requireComponent<HeldBy>().owner)
    }

    @Test
    fun skippedPlayerCannotClaimAnItemWhoseIdWasReused() = with(world) {
        val player = createPlayer()
        val removedItem = createItem()
        player.entity.requireComponent<PlayerInventory>().items += removedItem
        player.entity.requireComponent<Equipment>().slots[slot] = removedItem

        destroy(removedItem)
        val replacement = createItem()
        assertEquals(removedItem, replacement)

        tickItemOwnershipSystem(simulationFilter { false })

        assertNull(replacement.getComponent<HeldBy>())
        assertNull(replacement.getComponent<Location>())
    }

    @Test
    fun excludedPlayerCanResumeOwnershipAndMovementWhenSimulationIsAllowed() = with(world) {
        val player = createPlayer()
        val item = createItem()
        player.entity.requireComponent<PlayerInventory>().items += item
        val previouslyHeld = createItem()
        previouslyHeld.setComponent(HeldBy(player))
        previouslyHeld.setComponent(Location(Vec3(1, 2, 3)))
        var eligible = false
        val extension = simulationFilter { eligible }

        tickItemOwnershipSystem(extension)

        assertNull(item.getComponent<HeldBy>())
        assertEquals(1f, previouslyHeld.requireComponent<Location>().x)
        assertEquals(2f, previouslyHeld.requireComponent<Location>().y)
        assertEquals(3f, previouslyHeld.requireComponent<Location>().z)

        eligible = true
        tickItemOwnershipSystem(extension)

        assertSame(player, item.requireComponent<HeldBy>().owner)
        assertEquals(10f, previouslyHeld.requireComponent<Location>().x)
        assertEquals(20f, previouslyHeld.requireComponent<Location>().y)
        assertEquals(30f, previouslyHeld.requireComponent<Location>().z)
    }

    private fun simulationFilter(predicate: (EnginePlayer) -> Boolean) =
        object : EngineSimulation.SimulationTickExtension {
            override fun shouldSimulate(player: EnginePlayer) = predicate(player)
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
