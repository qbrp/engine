package org.lain.engine.client.render

import org.joml.Quaternionf
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.Vec2
import org.lain.engine.util.math.Vec3

interface Camera {
    val rotation: Quaternionf
    val pos: Vec3
    var shakeFrequency: Float
    var maxShakeTranslation: Vec3
    var maxShakeRotation: Vec2
    fun shake(effect: ShakeEffect)
    fun impulse(x: Float, y: Float)
    fun update(
        positionConsumer: (Vec3) -> Unit,
        rotationConsumer: (Vec2) -> Unit,
        dt: Float
    )
}

data class ShakeEffect(
    val trauma: Float,
    val frequency: Float,
    val duration: Float,
    val location: ShakeLocation? = null,
    var startTime: Long = System.currentTimeMillis()
)

data class ShakeLocation(val position: Pos, val radius: Float)