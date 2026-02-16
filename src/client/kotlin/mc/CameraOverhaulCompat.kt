package org.lain.engine.client.mc

import mirsario.cameraoverhaul.ScreenShakes
import org.lain.engine.util.math.Pos

fun isClassAvailable(className: String): Boolean {
    return try {
        Class.forName(className)
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}

fun isCameraOverhaulAvailable(): Boolean = isClassAvailable("mirsario.cameraoverhaul.CameraOverhaul")

fun createCameraOverhaulShakeSlot(trauma: Float, frequency: Float, length: Float, position: Pos?, radius: Float?) {
    val slot = ScreenShakes.createDirect()
    slot.trauma = trauma
    slot.frequency = frequency
    slot.lengthInSeconds = length
    position?.let { slot.position.set(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
    radius?.let { slot.radius = it }
}