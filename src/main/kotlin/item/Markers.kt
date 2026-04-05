package org.lain.engine.item

import org.lain.engine.player.EnginePlayer
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.get

data class HoldsBy(val owner: EnginePlayer) : Component

val EngineItem.owner get() = this.get<HoldsBy>()?.owner