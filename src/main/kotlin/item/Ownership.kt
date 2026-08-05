package org.lain.engine.item

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.collectOwnedItems
import org.lain.engine.util.addIfNotNull
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.pos

data class HeldBy(var owner: EnginePlayer?) : Component

context(world: World)
fun EngineItem.getOwner() = this.getComponent<HeldBy>()?.owner

fun World.tickItemOwnershipSystem() {
    iterate<Player, PlayerInventory, Location> { entity, (player), inventory, location ->
        val equipmentItems = player.collectOwnedItems().toMutableList()
        equipmentItems.addIfNotNull(inventory.cursorItem)
        val allItems = inventory.items + equipmentItems

        allItems.forEach { item ->
            val heldBy = item.getComponent<HeldBy>()
            if (heldBy == null) {
                item.setComponent(HeldBy(player))
            } else {
                heldBy.owner = player
            }
        }
    }

    iterate<Item, HeldBy>() { item, _, holdsBy ->
        val owner = holdsBy.owner
        if (owner != null) {
            val pos = owner.pos
            val location = item.getComponent<Location>() ?: run {
                val component = Location(pos)
                item.setComponent(component)
                component
            }
            location.position.set(pos.x, pos.y, pos.z)
        }
    }
}

fun World.resetItemOwnershipState() {
    iterate<Item, HeldBy> { _, _, holdsBy ->
        holdsBy.owner = null
    }
}