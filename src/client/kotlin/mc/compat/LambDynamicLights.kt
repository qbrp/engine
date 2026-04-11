package org.lain.engine.client.mc.compat

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.RaycastContext
import org.joml.Matrix3d
import org.joml.Vector3d
import org.lain.cyberia.ecs.get
import org.lain.engine.chat.acoustic.Grid3b
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.item.ConeLightEmitterSettings
import org.lain.engine.item.Flashlight
import org.lain.engine.item.ItemUuid
import org.lain.engine.mc.EntityTable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.handItem
import org.lain.engine.util.Injector
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
        val behaviour = FlashlightLightBehavior(owner, MinecraftClient, 0.02f, it.settings)
        context.dynamicLightBehaviorManager().add(behaviour)
        behaviours[it] = behaviour
    }
    deletedSources.forEach {
        val behavior = behaviours[it] ?: return@forEach
        context.dynamicLightBehaviorManager().remove(behavior)
    }
    lastSources.clear()
    lastSources.addAll(sourceList)

    behaviours.forEach { (source, behaviour) ->
        if (behaviour is FlashlightLightBehavior) {
            behaviour.tick()
        }
    }
}


class FlashlightLightBehavior(
    private val entity: Entity,
    private val client: MinecraftClient,
    private val epsilon: Float,
    val settings: ConeLightEmitterSettings
) : DynamicLightBehavior {
    private val radius = settings.radius
    private val depth = settings.distance
    private val light = settings.light

    private var prevX = 0.0
    private var prevY = 0.0
    private var prevZ = 0.0
    private var prevYaw = 0f
    private var prevPitch = 0f
    private var rotationMatrix: Matrix3d? = null
    private var inverseRotationMatrix: Matrix3d? = null

    private var updatePassability = false
    @Volatile
    private var passability = Grid3b(1, 1 ,1)
    private var boundingBox = DynamicLightBehavior.BoundingBox(1, 1, 1, 1, 1, 1)

    init {
        this.computeMatrices()
    }

    fun tick() {
        if (!updatePassability) return
        updatePassability = false
        passability = Grid3b(
            abs(boundingBox.endX - boundingBox.startX),
            abs(boundingBox.endY - boundingBox.startY),
            abs(boundingBox.endZ - boundingBox.startZ)
        ) { false }

        val blockPos = BlockPos.Mutable()
        val vector = Vector3d()
        passability.forEach { idx, x, y, z ->
            blockPos.set(x + boundingBox.startX, y + boundingBox.startY, z + boundingBox.startZ)
            vector.set(0.5 + blockPos.x, 0.5 + blockPos.y, 0.5 + blockPos.z)
            val coord = this.worldToEntitySpace(vector)
            if (abs(coord.x()) > radius || coord.y() < 0 || coord.y() > depth) return@forEach
            if (coord.x*coord.x + coord.z*coord.z > radius*radius) return@forEach
            val sdf: Double = (radius * (0.5f - coord.y() / depth) - sqrt(coord.x() * coord.x() + coord.z() * coord.z())).coerceAtMost(depth * 0.5f - abs(coord.y()))
            if (sdf < 0) return@forEach

            val context = RaycastContext(
                entity.eyePos,
                blockPos.toCenterPos(),
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.WATER,
                ShapeContext.absent()
            )
            val raycastResult = entity.entityWorld.raycast(context)
            val result = raycastResult != null && (raycastResult.type == HitResult.Type.MISS || raycastResult.blockPos == blockPos)
            passability[idx] = result
        }
    }

    private fun computeRaycastArrayValue(pos: BlockPos): Boolean {
        val lX = pos.x - boundingBox.startX
        val lY = pos.y - boundingBox.startY
        val lZ = pos.z - boundingBox.startZ
        return try {
            if (!passability.inBounds(lX, lY, lZ)) return false
            passability[lX, lY, lZ]
        } catch (e: Exception) {
            false
        }
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

        val lightLevel = if (intensity > 0.01f) {
           if (computeRaycastArrayValue(pos)) {
               intensity * light
           } else {
               0.0
           }
        } else {
            0.0
        }

        return Math.clamp(smoothstepSDF(sdf), 0f, 1f) * lightLevel
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
                    val vector = this.entityToWorldSpace(Vector3d(x, y, z))
                    vectors.add(vector)
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
        ).also { boundingBox = it }
    }

    override fun hasChanged(): Boolean {
        val diffYaw = abs(entity.yaw - prevYaw)
        val diffPitch = abs(entity.pitch - prevPitch)

        if (
            abs(entity.x - this.prevX) >= epsilon
            || abs(entity.y - this.prevY) >= epsilon
            || abs(entity.z - this.prevZ) >= epsilon
            || diffYaw >= epsilon
            || diffPitch >= epsilon
        ) {
            this.updatePassability = true

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