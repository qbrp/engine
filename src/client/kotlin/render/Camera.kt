package org.lain.engine.client.render

import org.joml.Quaternionf
import org.lain.engine.util.Pos
import org.lain.engine.util.Vec3

interface Camera {
    val rotation: Quaternionf
    val pos: Vec3
}