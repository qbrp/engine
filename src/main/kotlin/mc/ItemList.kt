package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.util.Identifier
import org.lain.engine.util.injectItemContext


@JvmInline
value class ItemId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

@JvmInline
value class ItemNamespaceId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

data class ItemNamespace(
    val id: ItemNamespaceId,
    val items: List<ItemId>
)

class EngineItemRegistry {
    var identifiers: List<String> = listOf()
        private set
    var stacks: Map<ItemId, ItemStack> = mapOf()
        private set
    var namespaces: Map<ItemNamespaceId, ItemNamespace> = mapOf()
        private set

    fun upload(
        items: List<EngineItem>,
        namespaces: List<ItemNamespace>
    ) {
        val ids = mutableListOf<String>()
        stacks = items.associate {
            ids += it.id.value
            it.id to getItemStack(it)
        }
        this.namespaces = namespaces.associateBy {
            ids += it.id.value
            it.id
        }
        identifiers = ids
    }
}

fun PlayerEntity.hasItemListPermission() = hasPermission("engine.itemlist")

data class ItemListTab(
    val id: String,
    val name: String,
    val items: List<ItemId>
)

data class EngineItemContext(
    val itemRegistry: EngineItemRegistry,
    var tabs: List<ItemListTab>
)

//

class ItemListTabInventoryAdapter(
    var tab: ItemListTab,
    val itemRegistry: EngineItemRegistry
) : Inventory {
    private val itemList = tab.items

    private fun itemStackOf(slot: Int) = itemRegistry.stacks[itemList[slot]]

    override fun size(): Int = itemList.size

    override fun isEmpty(): Boolean = itemList.isEmpty()

    override fun getStack(slot: Int): ItemStack? {
        return itemStackOf(slot)
    }

    override fun removeStack(slot: Int, amount: Int): ItemStack? {
        return itemStackOf(slot)?.copyWithCount(amount)
    }

    override fun removeStack(slot: Int): ItemStack? {
        return itemStackOf(slot)?.copy()
    }

    override fun setStack(slot: Int, stack: ItemStack?) {}

    override fun markDirty() {}

    override fun canPlayerUse(player: PlayerEntity): Boolean {
        return player.hasItemListPermission()
    }

    override fun clear() {}
}

class CreativeGiveSlot(
    inventory: Inventory,
    private val indexProvider: () -> Int
) : Slot(inventory, 0, 0, 0) {

    override fun hasStack(): Boolean {
        return indexProvider() in 0 until inventory.size()
    }

    override fun getStack(): ItemStack {
        val index = indexProvider()
        return inventory.getStack(index)?.copy() ?: ItemStack.EMPTY
    }

    override fun onTakeItem(player: PlayerEntity, stack: ItemStack) {
        if (!player.inventory.insertStack(stack.copy())) {
            player.dropItem(stack.copy(), false)
        }
    }

    override fun canInsert(stack: ItemStack): Boolean = false
}

private const val COLUMNS = 9
private const val ROWS = 5
private const val SLOTS_PER_PAGE = COLUMNS * ROWS

class ItemListScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val tabs: List<ItemListTab>,
    private val registry: EngineItemRegistry
) : ScreenHandler(TYPE, syncId) {

    private var tabIndex = 0
        set(value) {
            adapter.tab = tabs[value]
            field = value
        }
    private var page = 0
    private val adapter: ItemListTabInventoryAdapter = ItemListTabInventoryAdapter(tabs[tabIndex], registry)

    init {
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                val slotIndex = row * COLUMNS + col
                val x = 8 + col * 18
                val y = 18 + row * 18

                addSlot(
                    CreativeGiveSlot(adapter) {
                        page * SLOTS_PER_PAGE + slotIndex
                    }.apply {
                        this.x = x
                        this.y = y
                    }
                )
            }
        }

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(
                    Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        140 + row * 18
                    )
                )
            }
        }

        for (col in 0 until 9) {
            addSlot(
                Slot(
                    playerInventory,
                    col,
                    8 + col * 18,
                    198
                )
            )
        }
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return player.hasItemListPermission()
    }

    override fun onButtonClick(player: PlayerEntity, id: Int): Boolean {
        when (id) {
            0 -> prevPage()
            1 -> nextPage()
            in 100 until 100 + tabs.size -> selectTab(id - 100)
        }
        return true
    }

    private fun maxPage(): Int {
        val size = adapter.size()
        return if (size == 0) 0 else (size - 1) / SLOTS_PER_PAGE
    }

    private fun nextPage() {
        if (page < maxPage()) page++
    }

    private fun prevPage() {
        if (page > 0) page--
    }

    private fun selectTab(index: Int) {
        if (index != tabIndex && index in tabs.indices) {
            tabIndex = index
            page = 0
        }
    }

    override fun quickMove(player: PlayerEntity, slot: Int): ItemStack {
        return ItemStack.EMPTY
    }

    companion object {
        val TYPE = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("engine", "creativeinventory"),
            ScreenHandlerType(
                { syncId, playerInventory ->
                    val itemContext by injectItemContext()
                    ItemListScreenHandler(syncId, playerInventory, itemContext.tabs, itemContext.itemRegistry)
                },
                FeatureSet.empty()
            )
        )
    }
}