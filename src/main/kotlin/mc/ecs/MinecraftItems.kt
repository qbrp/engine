package org.lain.engine.mc.ecs

import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.item.Count
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.player.DecrementItem
import org.lain.engine.world.World

data class MinecraftItem(
    var itemStacks: MutableList<ItemStack> = mutableListOf(),
    var synchronizedThisTick: Boolean = false
) : Component {
    fun itemStackToUpdate() = itemStacks.firstOrNull()?.takeIf { !synchronizedThisTick }
}

context(world: World)
fun EngineMinecraftServer.wrapItemStackCatching(itemId: ItemId, stack: ItemStack): EngineItem? {
    return try {
        wrapItemStack(itemId, stack)
    } catch (t: Throwable) {
        detachEngineItemStack(stack)
        MinecraftSystem.LOGGER.error("Не удалось создать engine-предмет $itemId", t)
        null
    }
}

fun World.resetMinecraftItemState() {
    iterate<MinecraftItem> { _, minecraftItem ->
        minecraftItem.synchronizedThisTick = false
        minecraftItem.itemStacks.clear()
    }
}

fun World.synchronizeItemStacks() {
    iterate<MinecraftItem, DecrementItem>() { entity, minecraftItem, decrementItem ->
        minecraftItem
            .itemStackToUpdate()
            ?.decrement(decrementItem.count)
        entity.removeComponent<DecrementItem>()
    }

    iterate<MinecraftItem, Count> { entity, minecraftItem, count ->
        val itemStack = minecraftItem.itemStackToUpdate() ?: return@iterate
        count.value = itemStack.count
    }

    synchronizeMinecraftItemStackVisuals()
}

fun World.synchronizeMinecraftItemStackVisuals() {
    iterate<MinecraftItem>() { entity, minecraftItem ->
        val itemStack = minecraftItem.itemStackToUpdate() ?: return@iterate
        minecraftItem.synchronizedThisTick = true
        updateEngineItemStack(itemStack, entity)
    }
}
