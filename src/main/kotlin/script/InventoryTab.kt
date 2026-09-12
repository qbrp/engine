package org.lain.engine.script

import org.lain.engine.item.ItemId

data class InventoryTab(val entries: List<Entry>) {
    data class Entry(
        val prefabId: ItemId,
        val tooltipLines: List<String>
    )
}