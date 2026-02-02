package org.lain.engine.player

import org.lain.engine.item.ItemUuid
import org.lain.engine.server.AttributeUpdate
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.Component
import org.lain.engine.util.flush
import org.lain.engine.util.get
import org.lain.engine.util.require
import java.util.concurrent.ConcurrentLinkedQueue

sealed class PlayerUpdate {
    data class CustomSpeedAttribute(val value: AttributeUpdate) : PlayerUpdate()
    data class CustomJumpStrengthAttribute(val value: AttributeUpdate) : PlayerUpdate()
    data class CustomName(val value: org.lain.engine.player.CustomName?) : PlayerUpdate()
    data class SpeedIntention(val value: Float) : PlayerUpdate()
    data class GunSelector(val item: ItemUuid, val selector: Boolean) : PlayerUpdate()
    data class GunBarrelBullets(val item: ItemUuid, val bullets: Int) : PlayerUpdate()
}

data class PlayerUpdates(
    val updates: ConcurrentLinkedQueue<PlayerUpdate> = ConcurrentLinkedQueue()
) : Component

fun EnginePlayer.markUpdate(update: PlayerUpdate) {
    get<PlayerUpdates>()?.updates += update
}

fun EnginePlayer.flushUpdates(todo: (PlayerUpdate) -> Unit) {
    require<PlayerUpdates>().updates.flush(todo)
}

fun flushPlayerUpdates(
    player: EnginePlayer,
    transport: ServerHandler
) {
    with(transport) {
        player.flushUpdates {
            when(it) {
                is PlayerUpdate.CustomJumpStrengthAttribute -> onPlayerJumpStrengthUpdate(player, it.value)
                is PlayerUpdate.CustomSpeedAttribute -> onPlayerCustomSpeedUpdate(player, it.value)
                is PlayerUpdate.CustomName -> onPlayerCustomName(player, it.value)
                is PlayerUpdate.SpeedIntention -> onPlayerSpeedIntention(player, it.value)
                is PlayerUpdate.GunSelector -> { transport.onItemGunUpdate(player, it.item, selector = it.selector) }
                is PlayerUpdate.GunBarrelBullets -> { transport.onItemGunUpdate(player, it.item, barrelBullets = it.bullets) }
            }
        }
    }
}