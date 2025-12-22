package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.flush
import org.lain.engine.util.require
import java.util.concurrent.ConcurrentLinkedQueue

data class CommandQueue(
    val atStart: ConcurrentLinkedQueue<() -> Unit> = ConcurrentLinkedQueue()
) : Component

fun Player.taskCommand(statement: () -> Unit) {
    require<CommandQueue>().atStart += statement
}

fun flushPlayerCommands(player: Player) {
    player.require<CommandQueue>().atStart.flush { it() }
}