package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.character.CharacterDisplay
import org.lain.engine.player.character.CharacterHeight
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.math.EVec3
import org.lain.engine.world.Location
import org.lain.engine.world.World

@Serializable
data class EnginePlayerModel(
    var lastTickScale: Float = 1f,
    var scale: Float = 1f,
    var standingEyeHeight: Float = 0.8f,
    var height: Float = 1f,
    var skinEyeY: Float = 0f,
) : Component

val EnginePlayer.eyePos: EVec3
    get() {
        val location = require<Location>()
        val standingEyeHeight = require<EnginePlayerModel>().standingEyeHeight
        return Vec3(location.x, location.y + standingEyeHeight, location.z)
    }

val EnginePlayer.skinEyeY: Float
    get() = this.require<EnginePlayerModel>().skinEyeY

val EnginePlayer.height
    get() = require<EnginePlayerModel>().height

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

private val CHARACTER_HEIGHT_TO_SCALE_MULTIPLIER = 0.5319149f

val CharacterHeight.scale
    get() = meters * CHARACTER_HEIGHT_TO_SCALE_MULTIPLIER

fun World.tickPlayerModelSystem() = iterate<EnginePlayerModel, CharacterDisplay>() { _, model, display ->
    model.scale = display.height.scale
}