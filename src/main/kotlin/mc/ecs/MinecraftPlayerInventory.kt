package org.lain.engine.mc.ecs

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.mc.ecs.ITEM_STACK_MATERIAL
import org.lain.engine.mc.ecs.MinecraftItem
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.mc.ecs.engineItem
import org.lain.engine.mc.ecs.wrapEngineItemStack
import org.lain.engine.player.GiveItemEvent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.get
import org.lain.engine.world.World

fun World.tickGiveItemSystem() {
    iterate<GiveItemEvent>() { _, (player, item, slot) ->
        val minecraftPlayer = player.get<MinecraftPlayer>() ?: return@iterate
        val minecraftEntity = minecraftPlayer.entity
        val entityInventory = minecraftEntity.inventory
        val itemStack = wrapEngineItemStack(item, ITEM_STACK_MATERIAL.copy())
        if (slot != null) {
            entityInventory.add(slot, itemStack)
        } else {
            entityInventory.add(itemStack)
        }
    }
}

fun World.tickMinecraftPlayerInventorySystem() {
    iterate<MinecraftPlayer, PlayerInventory> { _, (entity, itemStacks), inventory ->
        val mainItemStack = entity.mainHandItem
        val offItemStack = entity.offhandItem
        var mainHandItem: EngineItem? = null
        var offHandItem: EngineItem? = null
        val remainingPlayerInventoryItems = inventory.items.toMutableSet()

        for ((item, itemStack) in itemStacks) {
            if (mainItemStack?.engineItem() == item) {
                mainHandItem = item
            }
            if (offItemStack?.engineItem() == item) {
                offHandItem = item
            }

            inventory.items += item
            remainingPlayerInventoryItems -= item

            val minecraftItem = item.getComponent<MinecraftItem>()
            if (minecraftItem == null) {
                item.setComponent(MinecraftItem(mutableListOf(itemStack)))
            } else {
                minecraftItem.itemStacks += itemStack
            }
        }

        inventory.mainHandFree = mainItemStack.isEmpty
        inventory.mainHandItem = mainHandItem
        inventory.offHandItem = offHandItem
        inventory.selectedSlot = entity.inventory.selected

        for (removedItem in remainingPlayerInventoryItems) {
            if (removedItem != inventory.cursorItem) {
                inventory.items.remove(removedItem)
            }
        }
    }
}
