package org.lain.engine.client.mc

import net.minecraft.client.MinecraftClient
import org.joml.Quaternionf
import org.lain.engine.client.render.Camera
import org.lain.engine.mc.engine
import org.lain.engine.util.VEC3_ZERO
import org.lain.engine.util.Vec3

class MinecraftCamera(
    private val client: MinecraftClient,
) : Camera {
    override val rotation: Quaternionf
        get() = client.gameRenderer.camera.rotation
    override val pos: Vec3
        get() = client.cameraEntity?.pos?.engine() ?: VEC3_ZERO
}