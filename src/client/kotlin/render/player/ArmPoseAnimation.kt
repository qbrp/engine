package org.lain.engine.client.render.player

import dev.kosmx.playerAnim.api.TransformType
import dev.kosmx.playerAnim.api.layered.IAnimation
import dev.kosmx.playerAnim.core.util.Vec3f
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess
import net.minecraft.client.player.AbstractClientPlayer
import org.lain.engine.player.ArmPose
import org.lain.engine.util.math.easeOutQuadratic
import org.lain.engine.util.math.lerp

private const val ARM_POSE_ANIMATION_PRIORITY = 100
private const val ARM_POSE_TRANSITION_TICKS = 2f
private const val ARM_POSE_TRANSITION_STEP = 1f / ARM_POSE_TRANSITION_TICKS
private const val ARM_SPREAD = 0.05f
private const val ARM_PITCH_OFFSET = 0.1f
private const val HALF_PI = 1.5707964f

fun registerArmPoseAnimation() {
    PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register { player, animationStack ->
        animationStack.addAnimLayer(ARM_POSE_ANIMATION_PRIORITY, ArmPoseAnimation(player))
    }
}

private class ArmPoseAnimation(
    private val player: AbstractClientPlayer,
) : IAnimation {
    private var headRotation = Vec3f.ZERO
    private val rightArmTransition = ArmTransition()
    private val leftArmTransition = ArmTransition()

    override fun isActive(): Boolean {
        val renderState = player.getEngineRenderState()
        return renderState?.mainArmPose == ArmPose.EXPOSE ||
                renderState?.minorArmPose == ArmPose.EXPOSE ||
                rightArmTransition.isActive ||
                leftArmTransition.isActive
    }

    override fun tick() {
        val renderState = player.getEngineRenderState()
        rightArmTransition.tick(renderState?.mainArmPose == ArmPose.EXPOSE)
        leftArmTransition.tick(renderState?.minorArmPose == ArmPose.EXPOSE)
    }

    override fun setupAnim(tickDelta: Float) {
        headRotation = Vec3f.ZERO
        rightArmTransition.setup(tickDelta)
        leftArmTransition.setup(tickDelta)
    }

    override fun get3DTransform(
        modelName: String,
        type: TransformType,
        tickDelta: Float,
        value0: Vec3f,
    ): Vec3f {
        if (modelName == "head" && type == TransformType.ROTATION) {
            headRotation = value0
            return value0
        }

        val isRight = when (modelName) {
            "rightArm" -> true
            "leftArm" -> false
            else -> return value0
        }
        val progress = if (isRight) rightArmTransition.renderProgress else leftArmTransition.renderProgress
        if (progress == 0f) return value0

        val target = when (type) {
            TransformType.ROTATION -> Vec3f(
                -HALF_PI + headRotation.getX() + ARM_PITCH_OFFSET,
                headRotation.getY() + if (isRight) -ARM_SPREAD else ARM_SPREAD,
                value0.getZ(),
            )

            TransformType.BEND -> Vec3f.ZERO
            else -> return value0
        }
        return interpolate(value0, target, progress)
    }
}

private class ArmTransition {
    private var previousProgress = 0f
    private var progress = 0f

    var renderProgress = 0f
        private set

    val isActive: Boolean
        get() = previousProgress > 0f || progress > 0f

    fun tick(exposed: Boolean) {
        previousProgress = progress
        val direction = if (exposed) 1f else -1f
        progress = (progress + direction * ARM_POSE_TRANSITION_STEP).coerceIn(0f, 1f)
    }

    fun setup(tickDelta: Float) {
        val interpolated = lerp(previousProgress, progress, tickDelta.coerceIn(0f, 1f))
        renderProgress = easeOutQuadratic(interpolated)
    }
}

private fun interpolate(from: Vec3f, to: Vec3f, progress: Float) = Vec3f(
    lerp(from.getX(), to.getX(), progress),
    lerp(from.getY(), to.getY(), progress),
    lerp(from.getZ(), to.getZ(), progress),
)
