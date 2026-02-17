package org.lain.engine.mc

import net.minecraft.block.ShapeContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.RaycastContext
import org.lain.engine.item.BULLET_FIRE_RADIUS
import org.lain.engine.item.GunShoot
import org.lain.engine.util.flush
import org.lain.engine.world.*

fun raycastBulletEvent(world: net.minecraft.world.World, event: GunShoot): BlockHitResult? {
    val start = event.start
    val end = start.add(event.vector.mul(BULLET_FIRE_RADIUS))
    val results = world.raycast(
        RaycastContext(
            start.toMinecraft(),
            end.toMinecraft(),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            ShapeContext.absent()
        )
    )
    return if (world.getBlockState(results.blockPos).isAir) {
        null
    } else {
        results
    }
}

fun net.minecraft.util.math.Direction.engine() = when(this) {
    net.minecraft.util.math.Direction.DOWN -> Direction.DOWN
    net.minecraft.util.math.Direction.UP -> Direction.UP
    net.minecraft.util.math.Direction.NORTH -> Direction.NORTH
    net.minecraft.util.math.Direction.SOUTH -> Direction.SOUTH
    net.minecraft.util.math.Direction.WEST -> Direction.WEST
    net.minecraft.util.math.Direction.EAST -> Direction.EAST
}

val BULLET_DAMAGE_DECALS_LAYER = DecalsLayerType("bullet-damage", 16)
const val MINIMUM_BULLET_DECAL_OPACITY = 0.7f

fun updateBullets(
    world: World,
    mcWorld: ServerWorld,
) = world.events<GunShoot>().flush { event ->
    val hitResult = raycastBulletEvent(mcWorld, event) ?: return@flush
    val blockPos = hitResult.blockPos
    val pos = hitResult.pos
    val dir = hitResult.side.engine()
    world.attachBulletDamageDecal(dir, pos.engine(), blockPos.engine())
}