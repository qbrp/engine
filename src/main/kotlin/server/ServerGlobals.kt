package org.lain.engine.server

import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.MovementSettings
import org.lain.engine.player.VocalSettings
import java.io.File

data class ServerGlobals(
    val serverId: ServerId,
    val savePath: File,
    val playerSynchronizationRadius: Int = 64,
    val playerDesynchronizationThreshold: Int = 8,
    val defaultPlayerAttributes: DefaultPlayerAttributes = DefaultPlayerAttributes(),
    val vocalSettings: VocalSettings = VocalSettings(),
    val movementSettings: MovementSettings = MovementSettings(),
    val chatSettings: EngineChatSettings = EngineChatSettings(),
    val requireIdenticalNamespaces: Boolean = false,
)
