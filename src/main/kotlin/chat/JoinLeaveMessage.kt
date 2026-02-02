package org.lain.engine.chat

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.username
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.world.world

fun EngineChat.trySendJoinMessage(player: EnginePlayer) {
    val msg = settings.joinMessage
    if (!settings.joinMessageEnabled && msg != "") return
    sendPlayerSystemMessage(msg, player)
}

fun EngineChat.trySendLeaveMessage(player: EnginePlayer) {
    val msg = settings.leaveMessage
    if (!settings.leaveMessageEnabled && msg != "") return
    sendPlayerSystemMessage(msg, player)
}

private fun EngineChat.sendPlayerSystemMessage(content: String, player: EnginePlayer) {
    if (content.isEmpty()) return
    processSystemMessage(
        content
            .replace("{player_name}", player.displayNameMiniMessage)
            .replace("{player_username}", player.username),
        player.world,
    )
}