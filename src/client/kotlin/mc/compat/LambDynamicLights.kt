package org.lain.engine.client.mc.compat

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior
import net.minecraft.block.ShapeContext
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import org.jetbrains.annotations.Range
import org.joml.Matrix3d
import org.joml.Vector3d
import org.lain.cyberia.ecs.*
import org.lain.engine.chat.acoustic.Grid3b
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.item.getOwner
import org.lain.engine.mc.toMinecraft
import org.lain.engine.player.Orientation
import org.lain.engine.util.Injector
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.inject
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.smoothstepSDF
import org.lain.engine.world.*
import java.util.*
import kotlin.math.*

fun injectDynamicLightsContext() = inject<DynamicLightsContext>()

class LambDynamicLights : DynamicLightsInitializer {
    override fun onInitializeDynamicLights(p0: DynamicLightsContext) {
        Injector.register(p0)
    }
}

data class LamdLightSource(val behaivour: EngineDynamicLightBehavior) : Component

class LightSystem(private val context: DynamicLightsContext) {
    private val dynamicLightBehaviours = IdentityHashMap<LamdLightSource, DynamicLightBehavior>()

    fun invalidate() {
        dynamicLightBehaviours.clear()
    }

    fun update(gameSession: GameSession) = with(gameSession.world) {
        val sourceList = mutableSetOf<LamdLightSource>()
        iterate<LightSource, Luminance, Location> { entity, lightSource, (luminance), location ->
            val source = entity.getComponent<LamdLightSource>() ?: run {
                val behaviour = getLamdDynLightsBehaviour(entity, lightSource.behaviour, luminance, location) ?: return@iterate
                LamdLightSource(behaviour)
                    .also { entity.setComponent(it) }
            }
            sourceList.add(source)
        }

        iterate<LightSource, LamdLightSource>() { entity, (engineBehaviour), source ->
            val lamdBehaviour = source.behaivour
            if (!dynamicLightBehaviours.contains(source)) {
                context.dynamicLightBehaviorManager().add(lamdBehaviour)
                dynamicLightBehaviours[source] = lamdBehaviour
            }
            lamdBehaviour.tick()
            if (lamdBehaviour is SphereLightBehaviour && engineBehaviour is LightBehaviour.Sphere) {
                lamdBehaviour.radius = engineBehaviour.radius
            }
        }

        iterate<Luminance, LamdLightSource> { entity, luminance, (lamdBehaviour) ->
            lamdBehaviour.luminance = luminance.value
        }

        iterate<Location, LamdLightSource> { entity, location, (lamdBehaviour) ->
            lamdBehaviour.position.set(location.position)
        }

        val deletedSources = dynamicLightBehaviours.keys.filter { !sourceList.contains(it) }
        deletedSources.forEach { source ->
            val behavior = dynamicLightBehaviours.remove(source) ?: return@forEach
            context.dynamicLightBehaviorManager().remove(behavior)
        }
    }

    context(world: org.lain.engine.world.World)
    private fun getLamdDynLightsBehaviour(
        entity: EntityId,
        behaviour: LightBehaviour,
        luminance: Int,
        location: Location
    ): EngineDynamicLightBehavior? = when (behaviour) {
        is LightBehaviour.Cone -> {
            val owner = entity.getOwner() ?: return null
            val orientation = owner.get<Orientation>() ?: return null
            val location = owner.location
            val mcWorld = MinecraftClient.world ?: return null
            FlashlightLightBehavior(
                location.position,
                luminance,
                behaviour.radius,
                behaviour.distance,
                { orientation.yaw },
                { orientation.pitch },
                mcWorld
            )
        }
        is LightBehaviour.Sphere -> SphereLightBehaviour(location.position, luminance, behaviour.radius)
    }
}

abstract class EngineDynamicLightBehavior(var luminance: Int, position: Pos) : DynamicLightBehavior {
    protected val lastPosition = MutableVec3(position)
    protected var lastLuminance = luminance
    val position = MutableVec3(position)

    open fun tick() {
        lastPosition.set(position)
        lastLuminance = luminance
    }

    override fun hasChanged(): Boolean {
        return lastPosition != position || lastLuminance != luminance
    }
}

class SphereLightBehaviour(
    position: Pos,
    luminance: Int,
    var radius: Int,
) : EngineDynamicLightBehavior(luminance, position) {
    private var box = createBoundingbox()
    private var lastRadius: Int = radius

    private fun createBoundingbox(): DynamicLightBehavior.BoundingBox {
        val x = position.x.toInt()
        val y = position.y.toInt()
        val z = position.z.toInt()

        return DynamicLightBehavior.BoundingBox(
            x - radius - 1,
            y - radius - 1,
            z - radius - 1,
            x + radius + 1,
            y + radius + 1,
            z + radius + 1
        )
    }

    override fun lightAtPos(
        pos: BlockPos,
        falloffRatio: Double
    ): @Range(from = 0, to = 15) Double {
        val dx: Double = pos.x - position.x + 0.5
        val dy: Double = pos.y - position.y + 0.5
        val dz: Double = pos.z - position.z + 0.5

        val distanceSquared = dx * dx + dy * dy + dz * dz
        return max(
            this.luminance - sqrt(distanceSquared) * falloffRatio,
            0.0
        )
    }

    override fun getBoundingBox(): DynamicLightBehavior.BoundingBox = box

    override fun tick() {
        super.tick()
        lastRadius = radius
    }

    override fun hasChanged(): Boolean {
        val changed = super.hasChanged() || lastRadius != radius
        if (changed) {
            box = createBoundingbox()
        }
        return changed
    }
}

class FlashlightLightBehavior(
    position: Pos,
    luminance: Int,
    private val radius: Float,
    private val depth: Float,
    private val yawGetter: () -> Float,
    private val pitchGetter: () -> Float,
    private val world: World,
    private val epsilon: Float = 0.02f,
) : EngineDynamicLightBehavior(luminance, position) {

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

    override fun tick() {
        super.tick()
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
                position.toMinecraft(),
                blockPos.toCenterPos(),
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.WATER,
                ShapeContext.absent()
            )
            val raycastResult = world.raycast(context)
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
               intensity * luminance
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
        if (super.hasChanged()) return true
        val yaw = yawGetter()
        val pitch = pitchGetter()
        val diffYaw = abs(yaw - prevYaw)
        val diffPitch = abs(pitch - prevPitch)

        if (
            abs(position.x - this.prevX) >= epsilon
            || abs(position.y - this.prevY) >= epsilon
            || abs(position.z - this.prevZ) >= epsilon
            || diffYaw >= epsilon
            || diffPitch >= epsilon
        ) {
            this.updatePassability = true

            this.prevX = position.x.toDouble()
            this.prevY = position.y.toDouble()
            this.prevZ = position.z.toDouble()
            this.prevYaw = yaw
            this.prevPitch = pitch
            this.computeMatrices()

            return true
        }
        return false
    }

    private fun computeMatrices() {
        val matrix = Matrix3d()
        matrix.rotateZ(Math.toRadians(pitchGetter().toDouble()))
        matrix.rotateZ(-MathHelper.HALF_PI.toDouble())
        matrix.rotateY(Math.toRadians(yawGetter().toDouble()))
        matrix.rotateY(MathHelper.HALF_PI.toDouble())
        this.rotationMatrix = matrix
        this.inverseRotationMatrix = matrix.invert(Matrix3d())
    }

    // ! mutates !
    private fun worldToEntitySpace(vec: Vector3d): Vector3d {
        vec.sub(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
        vec.mul(this.rotationMatrix)
        vec.y += depth / 2 - DISTANCE_DELTA

        return vec
    }

    // ! mutates !
    private fun entityToWorldSpace(vec: Vector3d): Vector3d {
        vec.y -= depth / 2 - DISTANCE_DELTA
        vec.mul(this.inverseRotationMatrix)
        vec.add(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())

        return vec
    }

    companion object {
        private const val DISTANCE_DELTA = 3f
    }
}