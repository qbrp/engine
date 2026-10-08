package org.lain.engine.mc.ecs

import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.player.GiveItemEvent
import org.lain.engine.player.TransferItemEvent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.get
import org.lain.engine.player.require
import org.lain.engine.world.World

fun World.tickGiveItemSystem() {
    iterate<TransferItemEvent>() { _, (source, target, item, slot) ->
        val sourceEntity = source.get<MinecraftPlayer>()?.entity ?: return@iterate
        val targetEntity = target.get<MinecraftPlayer>()?.entity ?: return@iterate
        val sourceInventory = sourceEntity.inventory
        val targetInventory = targetEntity.inventory
        val sourceStack = sourceEntity.mainHandItem
        val targetSlot = slot ?: targetInventory.freeSlot

        if (sourceStack.isEmpty || sourceStack.engineItem() != item) return@iterate
        moveItemStack(sourceInventory, targetInventory, sourceStack, targetSlot)
    }

    iterate<GiveItemEvent> { _, (target, item) ->
        val entity = target.require<MinecraftPlayer>().entity
        val itemStack = ITEM_STACK_MATERIAL.copy()
        wrapEngineItemStack(item, itemStack)
        entity.inventory.add(itemStack)
    }
}

internal fun moveItemStack(
    source: Container,
    target: Container,
    itemStack: ItemStack,
    targetSlot: Int,
): Boolean {
    if (targetSlot !in 0..<target.containerSize || !target.getItem(targetSlot).isEmpty) return false
    val sourceSlot = (0..<source.containerSize).firstOrNull { source.getItem(it) === itemStack } ?: return false

    source.setItem(sourceSlot, ItemStack.EMPTY)
    target.setItem(targetSlot, itemStack)
    source.setChanged()
    target.setChanged()
    return true
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
