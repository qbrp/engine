package org.lain.engine.client.render

import org.joml.Quaternionf
import org.lain.engine.util.math.EVec3
import org.lain.engine.util.math.Vec2

interface Camera {
    val rotation: Quaternionf
    val pos: EVec3
    var shakeFrequency: Float
    var maxShakeTranslation: EVec3
    var maxShakeRotation: Vec2
    fun shake(effect: ShakeEffect)
    fun impulse(x: Float, y: Float)
    fun update(
        positionConsumer: (EVec3) -> Unit,
        rotationConsumer: (Vec2) -> Unit,
        dt: Float
    )
}