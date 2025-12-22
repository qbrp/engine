package org.lain.engine.world

import org.lain.engine.util.Component

data class Orientation(
    var yaw: Float = 0f,
    var pitch: Float = 0f
) : Component