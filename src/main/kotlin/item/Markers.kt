package org.lain.engine.item

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.world.World

data class HoldsBy(val owner: EnginePlayer) : Component

context(world: World)
fun EngineItem.getOwner() = this.getComponent<HoldsBy>()?.owner