package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.require
import kotlin.math.cos
import kotlin.math.sin

data class Orientation(
    var yaw: Float = 0f,
    var pitch: Float = 0f
) : Component {
    val rotationVector: Vec3
        get() {
            val f = pitch * (Math.PI.toFloat() / 180)
            val g = -yaw * (Math.PI.toFloat() / 180)
            val h = cos(g)
            val i = sin(g)
            val j = cos(f)
            val k = sin(f)
            return Vec3(i * j, -k, h * j)
        }
}

data class OrientationTranslation(var yaw: Float, var pitch: Float) : Component

fun EnginePlayer.translateRotation(yaw: Float = 0f, pitch: Float = 0f) {
    val translation = this.require<OrientationTranslation>()
    translation.yaw += yaw
    translation.pitch += pitch
}