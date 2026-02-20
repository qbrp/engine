package org.lain.engine.player

import org.lain.engine.server.AttributeUpdate
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.Component
import org.lain.engine.util.flush
import org.lain.engine.util.get
import org.lain.engine.util.require
import java.util.concurrent.ConcurrentLinkedQueue

@Deprecated("Использовать Synchronization API")
sealed class PlayerUpdate {
    data class CustomSpeedAttribute(val value: AttributeUpdate) : PlayerUpdate()
    data class CustomJumpStrengthAttribute(val value: AttributeUpdate) : PlayerUpdate()
}

@Deprecated("Использовать Synchronization API")
data class PlayerUpdates(
    val updates: ConcurrentLinkedQueue<PlayerUpdate> = ConcurrentLinkedQueue()
) : Component


@Deprecated("Использовать Synchronization API")
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
            }
        }
    }
}