package org.lain.engine.server

import org.lain.engine.chat.IncomingMessage
import org.lain.engine.player.EnginePlayer

interface ServerEventListener {
    fun onPlayerInstantiated(player: EnginePlayer)
    fun onChatMessage(message: IncomingMessage)
}