package org.lain.engine.mc

import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.item.ItemStack
import org.lain.engine.item.EngineItem
import org.lain.engine.item.merge
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.get

fun onSlotEngineItemClicked(
    cursorItem: EngineItem,
    item: EngineItem,
    slotStack: ItemStack,
    cursorStack: ItemStack,
    player: Player,
    clickType: ClickAction
): Boolean {
    val enginePlayer = player.getEngineState() ?: return false
    val world = enginePlayer.world
    val space = slotStack.maxStackSize - slotStack.count
    val playerInventory = enginePlayer.get<PlayerInventory>()
    return if (world.merge(item, cursorItem) && space > 0) {
        when (clickType) {
            ClickAction.PRIMARY -> {
                val toMove = minOf(cursorStack.count, space)
                slotStack.increment(toMove)
                cursorStack.decrement(toMove)
            }

            ClickAction.SECONDARY -> {
                slotStack.increment(1)
                cursorStack.decrement(1)
            }
        }
        if (cursorStack.isEmpty) {
            playerInventory?.cursorItem = null
        }
        true
    } else {
        false
    }
}