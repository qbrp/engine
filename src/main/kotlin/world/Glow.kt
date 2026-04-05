package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.engine.util.math.Vec3

sealed class GlowScattering {
    data class Radius(val r: Float): GlowScattering()
    data class Beam(val direction: Vec3, val r: Float, val angle: Float): GlowScattering()
}

sealed class Glow(val scattering: GlowScattering, val luminance: Float) : Component