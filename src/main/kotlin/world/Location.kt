package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentManager
import org.lain.cyberia.ecs.require
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.math.Pos

data class Location(
    var world: World,
    val position: MutableVec3
) : Component {
    constructor(world: World, pos: Pos) : this(world, MutableVec3(pos))

    val x get() = position.x
    val y get() = position.y
    val z get() = position.z

    data class Immutable(val world: World, val position: ImmutableVec3)
}

val ComponentManager.pos
    get() = this.require<Location>().position

val ComponentManager.world
    get() = this.require<Location>().world

val ComponentManager.location
    get() = this.require<Location>()