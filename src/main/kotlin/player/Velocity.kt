package org.lain.engine.player

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.require
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.util.math.EVec3

data class Velocity(
    val motion: MutableEVec3 = MutableEVec3(),
    val prev: MutableEVec3 = MutableEVec3(),
    var set: EVec3? = null,
) : Component

val EnginePlayer.velocity
    get() = this.require<Velocity>().motion