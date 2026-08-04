package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.markDirty
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.player.DestroyItemSignal
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.set
import org.lain.engine.world.World

@Serializable
data class Magazine(
    val capacity: Int,
    var bullets: Int,
    val ammunition: ItemId
) : Component

data class MagazineLoadAction(val player: EnginePlayer, val ammoItem: EngineItem) : Component

fun World.tickMagazineSystem() {
    iterate<Magazine, MagazineLoadAction>() { item, magazine, (player, ammoItem) ->
        val ammoCount = ammoItem.getComponent<Count>()?.value ?: 1
        val loadAmmoCount = ammoCount.coerceAtMost(magazine.capacity - magazine.bullets)

        if (loadAmmoCount > 0) {
            magazine.bullets = (magazine.bullets + loadAmmoCount)
                .coerceAtMost(magazine.capacity)
            item.removeComponent<MagazineLoadAction>()
            item.markDirty<Magazine>()
            player.set(DestroyItemSignal(ammoItem, loadAmmoCount))
        }
    }
}