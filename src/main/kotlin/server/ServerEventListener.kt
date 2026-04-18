package org.lain.engine.server

import org.lain.engine.chat.IncomingMessage
import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.world.World

interface ServerEventListener {
    context(world: World)
    fun onPlayerInstantiated(player: EnginePlayer)
    fun onChatMessage(message: IncomingMessage)
    fun onCompiled(contents: NamespacedStorage)
}