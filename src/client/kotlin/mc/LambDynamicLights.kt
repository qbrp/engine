package org.lain.engine.client.mc

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import org.joml.Matrix3d
import org.joml.Vector3d
import org.lain.engine.client.GameSession
import org.lain.engine.item.ConeLightEmitterSettings
import org.lain.engine.item.Flashlight
import org.lain.engine.item.ItemUuid
import org.lain.engine.mc.EntityTable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.handItem
import org.lain.engine.util.Injector
import org.lain.engine.util.component.get
import org.lain.engine.util.inject
import org.lain.engine.util.math.smoothstepSDF
import kotlin.math.*

fun injectDynamicLightsContext() = inject<DynamicLightsContext>()

class LambDynamicLights : DynamicLightsInitializer {
    override fun onInitializeDynamicLights(p0: DynamicLightsContext) {
        Injector.register(p0)
    }
}

data class LightSource(val owner: EnginePlayer, val item: ItemUuid, val settings: ConeLightEmitterSettings)

fun updateLights(
    gameSession: GameSession,
    context: DynamicLightsContext,
    entityTable: EntityTable,
    lastSources: MutableSet<LightSource>,
    behaviours: MutableMap<LightSource, DynamicLightBehavior>
) {
    val sourceList = mutableSetOf<LightSource>()
    // Сбор источников света
    gameSession.playerStorage.forEach { player ->
        val handItem = player.handItem ?: return@forEach

        val flashlight = handItem.get<Flashlight>()
        if (flashlight != null && flashlight.enabled) {
            sourceList.add(LightSource(player, handItem.uuid, flashlight.emitter))
        }
    }

    val newSources = sourceList.filter { !lastSources.contains(it) }
    val deletedSources = lastSources.filter { !sourceList.contains(it) }
    newSources.forEach {
        val owner = entityTable.client.getEntity(it.owner) ?: return@forEach
        val behaviour = FlashlightLightBehavior(owner, 0.02f, it.settings)
        context.dynamicLightBehaviorManager().add(behaviour)
        behaviours[it] = behaviour
    }
    deletedSources.forEach {
        val behavior = behaviours[it] ?: return@forEach
        context.dynamicLightBehaviorManager().remove(behavior)
    }
    lastSources.clear()
    lastSources.addAll(sourceList)
}

class FlashlightLightBehavior(
    private val entity: Entity,
    private val epsilon: Float,
    val settings: ConeLightEmitterSettings
) : DynamicLightBehavior {
    private val radius = settings.radius
    private val depth = settings.distance

    private var prevX = 0.0
    private var prevY = 0.0
    private var prevZ = 0.0
    private var prevYaw = 0f
    private var prevPitch = 0f
    private var rotationMatrix: Matrix3d? = null
    private var inverseRotationMatrix: Matrix3d? = null

    init {
        this.computeMatrices()
    }

    override fun lightAtPos(pos: BlockPos, falloffRatio: Double): Double {
        val x: Double = pos.x + 0.5
        val y: Double = pos.y + 0.5
        val z: Double = pos.z + 0.5

        val coord = this.worldToEntitySpace(Vector3d(x, y, z))

        // Signed distance field function for a vertical cone centered at (0, 0)
        val sdf: Double =
            (radius * (0.5f - coord.y() / depth) - sqrt(coord.x() * coord.x() + coord.z() * coord.z())).coerceAtMost(depth * 0.5f - abs(coord.y()))

        val distance: Double = depth / 2f - coord.y() - DISTANCE_DELTA
        val intensity = depth / distance.pow(1.2)
        val light = intensity * 15f

        return Math.clamp(smoothstepSDF(sdf), 0f, 1f) * light
    }

    override fun getBoundingBox(): DynamicLightBehavior.BoundingBox {
        // To calculate the bounding box, we create a cuboid in entity space encapsulating the entire source, then transform it to world space.
        // We then calculate the larger xyz aligned cuboid encapsulating the first one by taking the minimum and maximum of each x and y coordinate.
        val horizontalValues = doubleArrayOf(-radius.toDouble(), radius.toDouble())
        val yValues = doubleArrayOf(-ceil((depth / 2).toDouble()), floor((depth / 2).toDouble()))
        val vectors: ArrayList<Vector3d> = ArrayList()

        for (x in horizontalValues) {
            for (y in yValues) {
                for (z in horizontalValues) {
                    vectors.add(this.entityToWorldSpace(Vector3d(x, y, z)))
                }
            }
        }

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE

        for (vector in vectors) {
            if (vector.x() < minX) {
                minX = vector.x()
            }
            if (vector.y() < minY) {
                minY = vector.y()
            }
            if (vector.z() < minZ) {
                minZ = vector.z()
            }
            if (vector.x() > maxX) {
                maxX = vector.x()
            }
            if (vector.y() > maxY) {
                maxY = vector.y()
            }
            if (vector.z() > maxZ) {
                maxZ = vector.z()
            }
        }

        return DynamicLightBehavior.BoundingBox(
            MathHelper.floor(minX),
            MathHelper.floor(minY),
            MathHelper.floor(minZ),
            MathHelper.ceil(maxX),
            MathHelper.ceil(maxY),
            MathHelper.ceil(maxZ)
        )
    }

    override fun hasChanged(): Boolean {
        if (
            abs(entity.x - this.prevX) >= epsilon
            || abs(entity.y - this.prevY) >= epsilon
            || abs(entity.z - this.prevZ) >= epsilon
            || abs(entity.yaw - this.prevYaw) >= epsilon
            || abs(entity.pitch - this.prevPitch) >= epsilon
        ) {
            this.prevX = entity.x
            this.prevY = entity.y
            this.prevZ = entity.z
            this.prevYaw = entity.yaw
            this.prevPitch = entity.pitch
            this.computeMatrices()
            return true
        }
        return false
    }

    private fun computeMatrices() {
        val matrix = Matrix3d()
        matrix.rotateZ(Math.toRadians(entity.getPitch().toDouble()))
        matrix.rotateZ(-MathHelper.HALF_PI.toDouble())
        matrix.rotateY(Math.toRadians(entity.getYaw().toDouble()))
        matrix.rotateY(MathHelper.HALF_PI.toDouble())
        this.rotationMatrix = matrix
        this.inverseRotationMatrix = matrix.invert(Matrix3d())
    }

    // ! mutates !
    private fun worldToEntitySpace(vec: Vector3d): Vector3d {
        vec.sub(entity.getX(), entity.getEyeY(), entity.getZ())
        vec.mul(this.rotationMatrix)
        vec.y += depth / 2 - DISTANCE_DELTA

        return vec
    }

    // ! mutates !
    private fun entityToWorldSpace(vec: Vector3d): Vector3d {
        vec.y -= depth / 2 - DISTANCE_DELTA
        vec.mul(this.inverseRotationMatrix)
        vec.add(entity.getX(), entity.getEyeY(), entity.getZ())

        return vec
    }

    companion object {
        private const val DISTANCE_DELTA = 3f
    }
}