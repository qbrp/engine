package org.lain.engine.server

import org.lain.engine.chat.IncomingMessage
import org.lain.engine.player.Player

interface ServerEventListener {
    fun onPlayerInstantiated(player: Player)
    fun onChatMessage(message: IncomingMessage)
}