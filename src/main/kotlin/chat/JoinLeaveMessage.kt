package org.lain.engine.chat

import org.lain.engine.player.Player
import org.lain.engine.player.displayName
import org.lain.engine.player.username
import org.lain.engine.util.injectEngineServer
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.world.world

fun EngineChat.trySendJoinMessage(player: Player) {
    val msg = settings.joinMessage
    if (!settings.joinMessageEnabled && msg != "") return
    sendPlayerSystemMessage(msg, player)
}

fun EngineChat.trySendLeaveMessage(player: Player) {
    val msg = settings.leaveMessage
    if (!settings.leaveMessageEnabled && msg != "") return
    sendPlayerSystemMessage(msg, player)
}

private fun EngineChat.sendPlayerSystemMessage(content: String, player: Player) {
    if (content.isEmpty()) return
    processSystemMessage(
        content
            .replace("{player_name}", player.displayNameMiniMessage)
            .replace("{player_username}", player.username),
        player.world,
    )
}