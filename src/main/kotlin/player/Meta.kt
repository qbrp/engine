package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.require

data class DeveloperMode(var enabled: Boolean) : Component

var EnginePlayer.developerMode
    get() = this.require<DeveloperMode>().enabled
    set(value) {
        this.require<DeveloperMode>().enabled = value
    }