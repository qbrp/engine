package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.require

@Serializable
data class GameMaster(var enabled: Boolean = false) : Component

var EnginePlayer.isInGameMasterMode
    get() = this.require<GameMaster>().enabled
    set(value) {
        this.require<GameMaster>().enabled = value
    }