package org.lain.engine.server

import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.item.SoundEventId
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.MovementSettings
import org.lain.engine.player.VocalSettings
import java.io.File

data class ServerGlobals(
    val serverId: ServerId,
    var savePath: File,
    var playerSynchronizationRadius: Int = 48,
    var itemSynchronizationRadius: Int = 48,
    val defaultPlayerAttributes: DefaultPlayerAttributes = DefaultPlayerAttributes(),
    var vocalSettings: VocalSettings = VocalSettings(),
    var movementSettings: MovementSettings = MovementSettings(),
    var chatSettings: EngineChatSettings = EngineChatSettings(),
    var defaultItemSounds: Map<String, SoundEventId> = mapOf()
)
