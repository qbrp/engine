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
