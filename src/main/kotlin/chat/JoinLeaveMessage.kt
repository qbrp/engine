package org.lain.engine.chat

import org.lain.engine.mc.displayNameMiniMessage
import org.lain.engine.mc.displayNameText
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.username
import org.lain.engine.world.World

context(world: World)
fun EngineChat.trySendJoinMessage(player: EnginePlayer) {
    val msg = settings.joinMessage
    if (!settings.joinMessageEnabled && msg != "") return
    sendPlayerSystemMessage(msg, player)
}

context(world: World)
fun EngineChat.trySendLeaveMessage(player: EnginePlayer) {
    val msg = settings.leaveMessage
    if (!settings.leaveMessageEnabled && msg != "") return
    sendPlayerSystemMessage(msg, player)
}

context(world: World)
private fun EngineChat.sendPlayerSystemMessage(content: String, player: EnginePlayer) {
    if (content.isEmpty()) return
    processSystemMessage(
        content
            .replace("{player_name}", player.displayNameMiniMessage)
            .replace("{player_username}", player.entity.username()),
        player.world,
    )
}