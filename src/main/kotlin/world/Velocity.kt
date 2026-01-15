package org.lain.engine.world

import org.lain.engine.player.Player
import org.lain.engine.util.Component
import org.lain.engine.util.MutableVec3
import org.lain.engine.util.VEC3_ZERO
import org.lain.engine.util.Vec3
import org.lain.engine.util.require

data class Velocity(val motion: MutableVec3 = MutableVec3()) : Component

val Player.velocity
    get() = this.require<Velocity>().motion