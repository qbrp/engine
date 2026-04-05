package org.lain.engine.player

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.require
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.math.Vec3

data class Velocity(
    val motion: MutableVec3 = MutableVec3(),
    val prev: MutableVec3 = MutableVec3(),
    var set: Vec3? = null,
) : Component

val EnginePlayer.velocity
    get() = this.require<Velocity>().motion