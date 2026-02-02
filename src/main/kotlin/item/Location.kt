package org.lain.engine.item

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.items
import org.lain.engine.util.set
import org.lain.engine.world.location
import org.lain.engine.world.world

fun supplyPlayerInventoryItemsLocation(
    player: EnginePlayer,
    items: Set<EngineItem> = player.items
) {
    val playerLocation = player.location
    for (item in items) {
        val itemLocation = item.location
        itemLocation.position.set(playerLocation.position)
        itemLocation.world = player.world
    }
}