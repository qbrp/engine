package org.lain.engine.item

import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.get

data class HoldsBy(val owner: EnginePlayer) : Component

val EngineItem.owner get() = this.get<HoldsBy>()?.owner