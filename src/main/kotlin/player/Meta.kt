package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.require
import org.lain.engine.util.set

data class DeveloperMode(var enabled: Boolean) : Component

var Player.developerMode
    get() = this.require<DeveloperMode>().enabled
    set(value) {
        this.require<DeveloperMode>().enabled = value
    }