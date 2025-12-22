package org.lain.engine.client.mc

import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.text.OrderedText
import org.lain.engine.client.render.BLACK
import org.lain.engine.client.render.BLACK_TRANSPARENT
import org.lain.engine.client.render.PURPLE_TRANSPARENT

data class ItemList(
    val groups: List<CompiledItemGroup>,
    val renderGroupIndex: Int,
    val x: Float,
    val y: Float,
    val itemPerRowCount: Int
)

data class ItemListSlot(
    val item: ItemStack,
    val x: Int,
    val y: Int,
)

data class CompiledItemGroup(
    val name: OrderedText,
    val slots: List<ItemListSlot>
)

fun handleItemListClick(itemList: ItemList, screenX: Int, screenY: Int) {
    val group = itemList.groups[itemList.renderGroupIndex]

    val relativeX = (screenX - itemList.x).toInt()
    val relativeY = (screenY - itemList.y).toInt()

    group.slots.forEach {
        if (relativeX in it.x..it.x + 16 && relativeY in it.y..it.y + 16) {
            println("Click")
        }
    }
}

fun renderItemList(context: DrawContext, itemList: ItemList, mouseX: Int, mouseY: Int) {
    val x = itemList.x
    val y = itemList.y
    val matrices = context.matrices

    val renderGroup = itemList.groups.first()
    val slots = renderGroup.slots
    val itemPerRowCount = itemList.itemPerRowCount
    val rows = slots.count() / itemPerRowCount + 1

    val itemSize = 16
    val width = itemPerRowCount * itemSize
    val height = rows * itemSize

    matrices.push()
    matrices.translate(x, y, 0f)

    context.fill(-1, -1, width + 1, height + 1, BLACK)
    context.fillGradient(0, 0, width, height, BLACK_TRANSPARENT, PURPLE_TRANSPARENT)

    slots.forEach { slot ->
        context.drawItem(slot.item, slot.x, slot.y)
    }

    matrices.pop()
}