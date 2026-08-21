package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.DecrementItem
import org.lain.engine.world.World

@Serializable
data class Magazine(
    val capacity: Int,
    var bullets: Int,
    val ammunition: ItemId
) : Component

data class MagazineLoadAction(val ammoItem: EngineItem) : Component

fun World.tickMagazineActionSystem() {
    iterate<Magazine, MagazineLoadAction>() { item, magazine, (ammoItem) ->
        val ammoCount = ammoItem.getComponent<Count>()?.value ?: 1
        val loadAmmoCount = ammoCount.coerceAtMost(magazine.capacity - magazine.bullets)

        if (loadAmmoCount > 0) {
            magazine.bullets = (magazine.bullets + loadAmmoCount)
                .coerceAtMost(magazine.capacity)
            item.removeComponent<MagazineLoadAction>()
            item.markDirty<Magazine>()
            ammoItem.setComponent(DecrementItem(loadAmmoCount))
        }
    }
}