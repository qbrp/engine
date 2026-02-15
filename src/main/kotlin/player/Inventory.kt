package org.lain.engine.player

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.util.Component
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