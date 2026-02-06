package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.require

data class DeveloperMode(var enabled: Boolean, var acoustic: Boolean = false) : Component

var EnginePlayer.developerMode
    get() = this.require<DeveloperMode>().enabled
    set(value) {
        this.require<DeveloperMode>().enabled = value
    }

var EnginePlayer.acousticDebug
    get() = this.require<DeveloperMode>().let { it.acoustic && it.enabled }
    set(value) {
        this.require<DeveloperMode>().acoustic = value
    }