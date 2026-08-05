package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.world.World

@Serializable
enum class PlayerMode {
    DEFAULT,
    GM,
    SPECTATOR,
}

@Serializable
data class PlayerModeComponent(var mode: PlayerMode = PlayerMode.SPECTATOR) : Component {
    val isSpectator get() = mode == PlayerMode.SPECTATOR
    val isGameMaster get() = mode == PlayerMode.GM
}

val EnginePlayer.isInGameMasterMode: Boolean
    get() = with(world) { require<PlayerModeComponent>().isGameMaster }

object StartSpectatingMark : Component

object SpawnMark : Component

context(world: World)
fun EntityId.isSpectating(): Boolean = requireComponent<PlayerModeComponent>().isSpectator

val EnginePlayer.isSpectating: Boolean
    get() = with(world) { entity.isSpectating() }

fun EnginePlayer.stopSpectating() = with(world) {
    entity.setComponent(SpawnMark)
    entity.removeComponent<StartSpectatingMark>()
}

fun EnginePlayer.canChangeGameMode() = has<AppliedCharacter>()