package org.lain.engine.item

import org.lain.engine.player.PlayerId
import org.lain.engine.util.Component
import org.lain.engine.util.get

data class HoldsBy(val owner: PlayerId) : Component

val EngineItem.owner get() = this.get<HoldsBy>()?.owner