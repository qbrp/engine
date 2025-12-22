package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.Vec3
import org.lain.engine.util.require
import org.lain.engine.world.Location

data class PlayerModel(
    var scale: Float = 1f,
    var standingEyeHeight: Float = 0.8f,
    var height: Float = 1f
) : Component

val Player.eyePos: Vec3
    get() {
        val location = require<Location>()
        val standingEyeHeight = require<PlayerModel>().standingEyeHeight
        return Vec3(location.x, standingEyeHeight, location.y)
    }

val Player.height
    get() = require<PlayerModel>().height