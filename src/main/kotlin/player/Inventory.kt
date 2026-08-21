package org.lain.engine.player

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.item.EngineItem

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
    var selectedSlot: Int = 0,
) : Component

data class PlayerContainer(val containerId: EntityId) : Component

object PlayerContainerTag : Component

// Уничтожить предмет в инвентаре игрока, обработать игрой
// Назначать на предмет
data class DecrementItem(val count: Int = 1) : Component

// Событие
data class GiveItemEvent(val player: EnginePlayer, val item: EngineItem, val slot: Int?) : Component

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

val EnginePlayer.mainContainer
    get() = this.require<PlayerContainer>().containerId