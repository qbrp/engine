package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.require
import org.lain.engine.player.interaction.VerbType
import org.lain.engine.script.*

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

@Serializable
data class ScriptBindings(
    var attack: ScriptId? = null,
    var base: ScriptId? = null,
) : Component
