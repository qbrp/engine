package org.lain.engine.server

import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.VocalSettings

data class ServerGlobals(
    val serverId: ServerId,
    var playerSynchronizationRadius: Int = 48,
    val defaultPlayerAttributes: DefaultPlayerAttributes = DefaultPlayerAttributes(),
    var vocalSettings: VocalSettings = VocalSettings()
)
