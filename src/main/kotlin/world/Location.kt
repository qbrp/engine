package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.util.math.Pos

data class Location(val position: MutableEVec3) : Component {
    constructor(pos: Pos) : this(MutableEVec3(pos))

    val x get() = position.x
    val y get() = position.y
    val z get() = position.z

    data class Immutable(val world: World, val position: ImmutableEVec3)
}

context(read: ReadComponentAccess)
fun EntityId.pos(): Pos {
    return requireComponent<Location>().position
}

val EnginePlayer.location: Location
    get() = with(world) { entity.requireComponent<Location>() }

val EnginePlayer.pos: Pos
    get() = location.position
