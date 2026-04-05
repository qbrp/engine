package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.server.markDirty
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.require
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location

@Serializable
data class PlayerModel(
    var scale: Float = 1f,
    var standingEyeHeight: Float = 0.8f,
    var height: Float = 1f,
    var skinEyeY: Float = 0f,
) : Component

val EnginePlayer.eyePos: Vec3
    get() {
        val location = require<Location>()
        val standingEyeHeight = require<PlayerModel>().standingEyeHeight
        return Vec3(location.x, location.y + standingEyeHeight, location.z)
    }

var EnginePlayer.skinEyeY: Float
    get() = this.require<PlayerModel>().skinEyeY
    set(value) {
        this.require<PlayerModel>().skinEyeY = value
        markDirty<PlayerModel>()
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
    extend: Boolean,
    item: Boolean,
    main: Boolean,
    gun: Boolean,
    safetyOff: Boolean,
    gunLeft: Boolean
): ArmPose {
    return when {
        extend && (item || main) -> ArmPose.EXPOSE
        gun && !gunLeft && !extend && safetyOff -> ArmPose.HOLD_WEAPON
        else -> ArmPose.NEUTRAL
    }
}