package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.require
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.world.World

@Serializable
data class GameMaster(var enabled: Boolean = false) : Component

context(world: World)
fun EntityId.inGameMasterMode(): Boolean = requireComponent<GameMaster>().enabled

val EnginePlayer.isInGameMasterMode: Boolean
    get() = with(world) { entity.inGameMasterMode() }
