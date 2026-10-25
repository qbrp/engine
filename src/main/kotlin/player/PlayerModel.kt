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
    var eyeLevel: Int = 0,
) : Component

/**
 * @param upper Верхняя часть тела (совместимость с EmoteCraft)
 * @param left Левая часть тела (отражение предметов рук и ног)
 * @param arm Рука (применение бендов)
 * @param bend Применять ли поворот бенда (для нижних частей конечностей)
 * */
enum class PlayerPart(
    val modelName: String,
    val upper: Boolean,
    val left: Boolean = false,
    val bend: Boolean = false
) {
    HEAD("head", true),
    LEFT_ARM("leftArm", true, left = true),
    RIGHT_ARM("rightArm", true, left = false),
    LEFT_PALM("leftArm", true, left = true, bend = true),
    RIGHT_PALM("rightArm", true, left = false, bend = true),
    BODY("torso", false),
    LEFT_LEG("leftLeg", false, true),
    RIGHT_LEG("rightLeg", false),
    LEFT_FEET("leftLeg", false, true, bend = true),
    RIGHT_FEET("rightLeg", false, bend = true)
}

val EnginePlayer.eyePos: EVec3
    get() {
        val location = require<Location>()
        val standingEyeHeight = require<EnginePlayerModel>().standingEyeHeight
        return Vec3(location.x, location.y + standingEyeHeight, location.z)
    }

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
    handsHoldsMultipleItems: Boolean,
    holdsGunSafetyOff: Boolean,
    extendArm: Boolean,
    isMain: Boolean,
    holdsItem: Boolean,
    otherHandHoldsItem: Boolean,
): ArmPose {
    return if (!handsHoldsMultipleItems) {
        when {
            extendArm && (holdsItem || (isMain && !otherHandHoldsItem)) -> ArmPose.EXPOSE
            holdsGunSafetyOff -> ArmPose.HOLD_WEAPON
            else -> ArmPose.NEUTRAL
        }
    } else {
        if (extendArm || holdsGunSafetyOff) {
            ArmPose.EXPOSE
        } else {
            ArmPose.NEUTRAL
        }
    }
}

private val CHARACTER_HEIGHT_TO_SCALE_MULTIPLIER = 0.5319149f

val CharacterHeight.scale
    get() = meters * CHARACTER_HEIGHT_TO_SCALE_MULTIPLIER

fun World.tickPlayerModelSystem() = iterate<EnginePlayerModel, CharacterDisplay>() { _, model, display ->
    model.scale = display.height.scale
    model.eyeLevel = display.appearance.eyeLevel
}