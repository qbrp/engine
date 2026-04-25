package org.lain.engine.client.render

import org.lain.cyberia.ecs.Component
import org.lain.engine.item.ConeLightEmitterSettings

data class LightSource(val behaviour: LightBehaviour) : Component

data class Luminance(var value: Int) : Component

sealed class LightBehaviour {
    data class Cone(val settings: ConeLightEmitterSettings) : LightBehaviour()
    data class Sphere(val radius: Int) : LightBehaviour()
}