package org.lain.engine.client.mc.compat

import mirsario.cameraoverhaul.ScreenShakes
import org.lain.engine.util.isClassAvailable
import org.lain.engine.util.math.Pos

fun isCameraOverhaulAvailable(): Boolean = isClassAvailable("mirsario.cameraoverhaul.CameraOverhaul")

fun createCameraOverhaulShakeSlot(trauma: Float, frequency: Float, length: Float, position: Pos?, radius: Float?) {
    val slot = ScreenShakes.createDirect()
    slot.trauma = trauma
    slot.frequency = frequency
    slot.lengthInSeconds = length
    position?.let { slot.position.set(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
    radius?.let { slot.radius = it }
}