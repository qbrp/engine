package org.lain.engine.player

import org.lain.engine.util.component.Component
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.component.require

data class Velocity(
    val motion: MutableVec3 = MutableVec3(),
    val prev: MutableVec3 = MutableVec3(),
) : Component

val EnginePlayer.velocity
    get() = this.require<Velocity>().motion