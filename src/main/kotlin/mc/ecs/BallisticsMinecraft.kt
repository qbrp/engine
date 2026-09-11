package org.lain.engine.mc.ecs

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.BULLET_FIRE_RADIUS
import org.lain.engine.mc.engine
import org.lain.engine.mc.toMinecraft
import org.lain.engine.mc.voxelPos
import org.lain.engine.world.BulletFireEvent
import org.lain.engine.world.ShootGeometry
import org.lain.engine.world.World
import org.lain.engine.world.attachBulletDamageDecal

fun raycastBulletEvent(world: Level, event: ShootGeometry): BlockHitResult? {
    val start = event.start
    val end = start.add(event.vector.mul(BULLET_FIRE_RADIUS))
    val results = world.clip(
        ClipContext(
            start.toMinecraft(),
            end.toMinecraft(),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            CollisionContext.empty()
        )
    )
    return if (world.getBlockState(results.blockPos).isAir) {
        null
    } else {
        results
    }
}

fun tickBulletFireDecalSystem(
    world: World,
    mcWorld: ServerLevel,
) = world.iterate<BulletFireEvent> { _, event ->
    val hitResult = raycastBulletEvent(mcWorld, event.shoot) ?: return@iterate
    val blockPos = hitResult.blockPos
    val pos = hitResult.location
    val dir = hitResult.direction.engine()
    world.attachBulletDamageDecal(dir, pos.engine(), blockPos.voxelPos())
}