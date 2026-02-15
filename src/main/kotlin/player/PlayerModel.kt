package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.require
import org.lain.engine.world.Location

data class PlayerModel(
    var scale: Float = 1f,
    var standingEyeHeight: Float = 0.8f,
    var height: Float = 1f
) : Component

val EnginePlayer.eyePos: Vec3
    get() {
        val location = require<Location>()
        val standingEyeHeight = require<PlayerModel>().standingEyeHeight
        return Vec3(location.x, location.y + standingEyeHeight, location.z)
    }

val EnginePlayer.height
    get() = require<PlayerModel>().height

enum class ArmPose {
    NEUTRAL, EXPOSE, HOLD_WEAPON
}

@Serializable
data class ArmStatus(var extend: Boolean) : Component

var EnginePlayer.extendArm
    get() = this.require<ArmStatus>().extend
    set(value) {
        this.require<ArmStatus>().extend = value
    }

fun armPoseOf(
    main: Boolean,
    extend: Boolean,
    gun: Boolean,
    safetyOff: Boolean
): ArmPose {
    return when {
        gun && !safetyOff -> ArmPose.NEUTRAL
        extend && (gun || main) -> ArmPose.EXPOSE
        gun -> ArmPose.HOLD_WEAPON
        else -> ArmPose.NEUTRAL
    }
}