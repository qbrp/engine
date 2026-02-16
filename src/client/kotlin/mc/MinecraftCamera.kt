package org.lain.engine.client.mc

import net.minecraft.client.MinecraftClient
import org.joml.Quaternionf
import org.lain.engine.client.render.Camera
import org.lain.engine.client.render.ShakeEffect
import org.lain.engine.mc.engine
import org.lain.engine.util.math.*
import kotlin.math.absoluteValue
import kotlin.math.pow


class MinecraftCamera(
    private val client: MinecraftClient,
    override var shakeFrequency: Float = 15f,
    override var maxShakeTranslation: Vec3 = Vec3(1f).mul(3f),
    override var maxShakeRotation: Vec2 = Vec2(1f).scale(3f),
) : Camera {
    private var time = 0f
    private val frequency = 0.7f
    private val perlinX1 = PerlinNoise(0)
    private val perlinY1 = PerlinNoise(1)
    private val perlinZ1 = PerlinNoise(2)
    private val perlinX2 = PerlinNoise(3)
    private val perlinY2 = PerlinNoise(4)
    private var impulseX: Float = 0f
    private var impulseY: Float = 0f
    private var shakeEffects: MutableList<ShakeEffect> = mutableListOf()

    override val rotation: Quaternionf
        get() = client.gameRenderer.camera.rotation
    override val pos: Vec3
        get() = client.cameraEntity?.entityPos?.engine() ?: VEC3_ZERO

    override fun shake(effect: ShakeEffect) {
        if (isCameraOverhaulAvailable()) {
            createCameraOverhaulShakeSlot(
                effect.trauma,
                effect.frequency * 15f,
                effect.duration,
                effect.location?.position,
                effect.location?.radius
            )
        } else {
            shakeEffects.add(effect)
            effect.startTime = this.time.toLong()
        }
    }

    override fun impulse(x: Float, y: Float) {
        impulseX += x
        impulseY += y
    }

    override fun update(positionConsumer: (Vec3) -> Unit, rotationConsumer: (Vec2) -> Unit, dt: Float) {
        time += dt

        val noise = Vec3(0.0f)
        var totalIntensity = 0f

        shakeEffects.removeIf { effect ->
            // delta tick
            val progress = if (effect.duration > 0f) (time - effect.startTime) / (effect.duration * 50) else 1f
            if (progress >= 1f) {
                true
            } else {
                var intensity = effect.trauma * (1f - progress).pow(2)
                effect.location?.let { loc ->
                    val distance = pos.asVec3().distance(loc.position)
                    val distanceFactor = 1f - (distance / (loc.radius)).coerceAtMost(1f)
                    intensity *= distanceFactor * distanceFactor
                }
                if (intensity > 0f && intensity.isFinite()) {
                    val sampleStep = time * effect.frequency
                    noise.add(
                        perlinX1.noise(sampleStep) * intensity,
                        perlinY1.noise(sampleStep) * intensity,
                        perlinZ1.noise(sampleStep) * intensity
                    )
                    totalIntensity += intensity
                }
                false
            }
        }

        if (totalIntensity > 1f) {
            noise.div(totalIntensity)
        }

        val finalPosition = noise.mul(maxShakeTranslation)
        val finalRotation = Vec2(
            perlinX2.noise(time) * 2 - 1,
            perlinY2.noise(time) * 2 - 1
        )
            .scale(maxShakeRotation)
            .scale(totalIntensity)
            .add(impulseX, impulseY)

        positionConsumer(finalPosition)
        rotationConsumer(finalRotation)

        impulseX = lerp(impulseX, 0f, 1f - 0.62f.pow(dt))
        impulseY = lerp(impulseY, 0f, 1f - 0.62f.pow(dt))
        if (impulseX.absoluteValue < 0.001f) impulseX = 0f
        if (impulseY.absoluteValue < 0.001f) impulseY = 0f
    }
}