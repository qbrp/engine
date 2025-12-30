package org.lain.engine.server

import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.MovementSettings
import org.lain.engine.player.VocalSettings
import java.util.concurrent.atomic.AtomicReference

data class ServerGlobals(
    val serverId: ServerId,
    var playerSynchronizationRadius: Int = 48,
    val defaultPlayerAttributes: DefaultPlayerAttributes = DefaultPlayerAttributes(),
    var vocalSettings: VocalSettings = VocalSettings(),
    var movementSettings: MovementSettings = MovementSettings(),
    var chatSettings: EngineChatSettings = EngineChatSettings()
)
