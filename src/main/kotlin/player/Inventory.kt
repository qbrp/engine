package org.lain.engine.player

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.merge
import org.lain.engine.util.Component
import org.lain.engine.util.handle
import org.lain.engine.util.require

/**
 * # Инвентарь игрока
 * Содержит список предметов, зарегистрированных в Engine, которые находятся в инвентаре игрока
 */
data class PlayerInventory(
    val items: MutableSet<EngineItem>,
    var cursorItem: EngineItem? = null,
    var mainHandItem: EngineItem? = null,
    var offHandItem: EngineItem? = null
) : Component

// Уничтожить предмет в инвентаре игрока, обработать игрой
data class DestroyItemSignal(val item: ItemUuid, val count: Int = 1) : Component

val EnginePlayer.items: Set<EngineItem>
    get() = this.require<PlayerInventory>().let { it.items + listOfNotNull(it.cursorItem) }

val EnginePlayer.handItem
    get() = this.require<PlayerInventory>().mainHandItem

val EnginePlayer.cursorItem
    get() = this.require<PlayerInventory>().cursorItem

private val SLOT_MERGE_VERB = VerbId("slot_merge")

fun appendPlayerInventoryVerbs(player: EnginePlayer) {
    player.handle<VerbLookup> {
        val slotClick = slotClick ?: return@handle
        if (merge(slotClick.item, slotClick.cursorItem)) {
            verbs += VerbVariant(
                ItemVerb(
                    SLOT_MERGE_VERB,
                    "Объединить предметы",
                ),
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