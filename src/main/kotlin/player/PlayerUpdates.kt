package org.lain.engine.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    data class CustomName(val value: String?) : PlayerUpdate()
    data class SpeedIntention(val value: Float) : PlayerUpdate()
}

data class PlayerUpdatesComponent(
    val updates: ConcurrentLinkedQueue<PlayerUpdate> = ConcurrentLinkedQueue()
) : Component

fun Player.markUpdate(update: PlayerUpdate) {
    get<PlayerUpdatesComponent>()?.updates += update
}

fun Player.flushUpdates(todo: (PlayerUpdate) -> Unit) {
    require<PlayerUpdatesComponent>().updates.flush(todo)
}

fun flushPlayerUpdates(
    player: Player,
    transport: ServerHandler
) {
    with(transport) {
        player.flushUpdates {
            when(it) {
                is PlayerUpdate.CustomJumpStrengthAttribute -> onPlayerJumpStrengthUpdate(player, it.value)
                is PlayerUpdate.CustomSpeedAttribute -> onPlayerCustomSpeedUpdate(player, it.value)
                is PlayerUpdate.CustomName -> onPlayerCustomName(player, it.value)
                is PlayerUpdate.SpeedIntention -> onPlayerSpeedIntention(player, it.value)
            }
        }
    }
}