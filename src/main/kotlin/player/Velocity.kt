package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.require

data class Velocity(
    val motion: MutableVec3 = MutableVec3(),
    val prev: MutableVec3 = MutableVec3(),
) : Component

val EnginePlayer.velocity
    get() = this.require<Velocity>().motion