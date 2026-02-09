package org.lain.engine.client.mc

import net.minecraft.client.MinecraftClient
import org.joml.Quaternionf
import org.lain.engine.client.render.Camera
import org.lain.engine.mc.engine
import org.lain.engine.util.math.PerlinNoise
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.util.math.Vec2
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.math.lerp
import kotlin.math.pow


class MinecraftCamera(
    private val client: MinecraftClient,
    override var shakeFrequency: Float = 15f,
    override var maxShakeTranslation: Vec3 = Vec3(1f).mul(3f),
    override var maxShakeRotation: Vec2 = Vec2(1f).scale(3f),
) : Camera {
    private var time = 0f
    private val frequency = 15f
    private val perlinX1 = PerlinNoise(0)
    private val perlinY1 = PerlinNoise(1)
    private val perlinZ1 = PerlinNoise(2)
    private val perlinX2 = PerlinNoise(3)
    private val perlinY2 = PerlinNoise(4)
    private var stress = 0f
    private var impulseX: Float = 0f
    private var impulseY: Float = 0f

    override val rotation: Quaternionf
        get() = client.gameRenderer.camera.rotation
    override val pos: Vec3
        get() = client.cameraEntity?.entityPos?.engine() ?: VEC3_ZERO

    override fun stress(shake: Float) {
        stress += shake
    }

    override fun impulse(x: Float, y: Float) {
        impulseX += x
        impulseY += y
    }

    override fun update(positionConsumer: (Vec3) -> Unit, rotationConsumer: (Vec2) -> Unit, dt: Float) {
        time += dt
        positionConsumer(
            Vec3(
                perlinX1.noise(time * frequency) * 2 - 1,
                perlinY1.noise(time * frequency) * 2 - 1,
                perlinZ1.noise(time * frequency) * 2 - 1
            )
                .mul(maxShakeTranslation)
                .mul(stress)
        )
        rotationConsumer(
            Vec2(
                perlinX2.noise(time * frequency) * 2 - 1,
                perlinY2.noise(time * frequency) * 2 - 1,
            )
                .scale(maxShakeRotation)
                .scale(stress)
                .add(impulseX, impulseY)
        )

        stress = lerp(stress, 0f, 1f - 0.2f.pow(dt))
        impulseX = lerp(impulseX, 0f, 1f - 0.5f.pow(dt))
        impulseY = lerp(impulseY, 0f, 1f - 0.5f.pow(dt))
        if (stress < 0.01f) {
            stress = 0f
        }
        if (impulseX < 0.01f) {
            impulseX = 0f
        }
        if (impulseY < 0.01f) {
            impulseY = 0f
        }
    }
}