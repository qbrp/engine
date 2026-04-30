package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component

@Serializable
data class LightSource(val behaviour: LightBehaviour) : Component

@Serializable
data class Luminance(var value: Int) : Component

@Serializable
sealed class LightBehaviour {
    @Serializable
    data class Cone(
        val radius: Float = 8f,
        val distance: Float = 20f
    ) : LightBehaviour()
    @Serializable
    data class Sphere(val radius: Int) : LightBehaviour()
}