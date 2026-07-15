package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.world.World

object StartSpectatingMark : Component

object SpawnMark : Component

/**
 * # Наблюдение
 * Значение `isSpectating` устанавливается на стороне Minecraft исходя из режима игры.
 * Управление и вызов режима спектатора производится через компоненты `SpawnMark` и `StartSpectatingMark`
 * **Режим спектатора, в отличие от ГМа, доступен только в одном режиме игры.**
 */

@Serializable
data class Spectating(var enabled: Boolean = false) : Component

context(world: World)
fun EntityId.isSpectating(): Boolean = requireComponent<Spectating>().enabled

val EnginePlayer.isSpectating: Boolean
    get() = with(world) { entity.isSpectating() }

fun EnginePlayer.stopSpectating() = with(world) {
    entity.setComponent(SpawnMark)
    entity.removeComponent<StartSpectatingMark>()
}
