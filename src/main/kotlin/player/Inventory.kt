package org.lain.engine.player

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.merge
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.handle
import org.lain.engine.util.component.require

/**
 * # Инвентарь игрока
 * Содержит список предметов, зарегистрированных в Engine, которые находятся в инвентаре игрока
 */
data class PlayerInventory(
    val items: MutableSet<EngineItem>,
    var cursorItem: EngineItem? = null,
    var mainHandItem: EngineItem? = null,
    var offHandItem: EngineItem? = null,
    var mainHandFree: Boolean = false,
    var selectedSlot: Int = 0
) : Component

// Уничтожить предмет в инвентаре игрока, обработать игрой
data class DestroyItemSignal(val item: ItemUuid, val count: Int = 1) : Component

data class MoveItemSignal(val item: ItemUuid, val slot: Int) : Component

val EnginePlayer.items: Set<EngineItem>
    get() = this.require<PlayerInventory>().let { it.items + listOfNotNull(it.cursorItem) }

val EnginePlayer.handItem
    get() = this.require<PlayerInventory>().mainHandItem

val EnginePlayer.handFree
    get() = this.require<PlayerInventory>().mainHandFree

val EnginePlayer.cursorItem
    get() = this.require<PlayerInventory>().cursorItem

val EnginePlayer.selectedSlot
    get() = this.require<PlayerInventory>().selectedSlot

private val SLOT_MERGE_VERB = VerbType("slot_merge", "Объединить предметы")

fun appendPlayerInventoryVerbs(player: EnginePlayer) {
    player.handle<VerbLookup> {
        val slotClick = slotClick ?: return@handle
        if (merge(slotClick.item, slotClick.cursorItem)) {
            verbs += VerbVariant(
                SLOT_MERGE_VERB,
                slotClick
            )
        }
    }
}

fun handlePlayerInventoryInteractions(player: EnginePlayer) {
    player.handleInteraction(SLOT_MERGE_VERB) {
        player.require<PlayerInventory>().cursorItem = null
    }
}