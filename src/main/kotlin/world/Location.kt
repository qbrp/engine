package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.ComponentManager
import org.lain.engine.util.MutableVec3
import org.lain.engine.util.Pos
import org.lain.engine.util.Vec3
import org.lain.engine.util.asVec3
import org.lain.engine.util.require

data class Location(
    var world: World,
    val position: MutableVec3
) : Component {
    constructor(world: World, pos: Pos) : this(world, MutableVec3(pos))

    val x get() = position.x
    val y get() = position.y
    val z get() = position.z
}

val ComponentManager.pos
    get() = this.require<Location>().position

val ComponentManager.world
    get() = this.require<Location>().world

val ComponentManager.location
    get() = this.require<Location>()