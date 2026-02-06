package org.lain.engine.client.render

import org.joml.Quaternionf
import org.lain.engine.util.Pos
import org.lain.engine.util.Vec2
import org.lain.engine.util.Vec3

interface Camera {
    val rotation: Quaternionf
    val pos: Vec3
    var shakeFrequency: Float
    var maxShakeTranslation: Vec3
    var maxShakeRotation: Vec2
    fun stress(shake: Float)
    fun impulse(x: Float, y: Float)
    fun update(
        positionConsumer: (Vec3) -> Unit,
        rotationConsumer: (Vec2) -> Unit,
        dt: Float
    )
}