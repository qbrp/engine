package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer

data class CommandInvoke(val command: String) : Component

data class PlayerCommandAccess(val player: EnginePlayer, val root: Boolean) : Component

fun World.invokeCommand(command: String) = emitEvent(CommandInvoke(command))

fun EnginePlayer.invokeCommand(command: String, root: Boolean) = with(world) {
    val event = invokeCommand(command)
    event.setComponent(PlayerCommandAccess(this@invokeCommand, root))
}