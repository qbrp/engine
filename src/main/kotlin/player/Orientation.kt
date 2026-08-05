package org.lain.engine.player

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.require
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.math.EVec3
import kotlin.math.cos
import kotlin.math.sin

data class Orientation(
    var yaw: Float = 0f, //readonly проекция
    var pitch: Float = 0f, //readonly проекция
    var translationYaw: Float = 0f, //writable
    var translationPitch: Float = 0f //writable
) : Component {
    val rotationVector: EVec3
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

fun EnginePlayer.translateRotation(yaw: Float = 0f, pitch: Float = 0f) {
    val translation = this.require<Orientation>()
    translation.translationYaw += yaw
    translation.translationPitch += pitch
}