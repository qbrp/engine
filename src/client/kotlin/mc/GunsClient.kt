package org.lain.engine.client.mc

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.ParticleTypes
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.BulletFire
import org.lain.engine.item.BulletParameters
import org.lain.engine.item.DEFAULT_BULLET_MASS
import org.lain.engine.item.DEFAULT_BULLET_SPEED
import org.lain.engine.mc.engine
import org.lain.engine.mc.raycastBulletEvent
import org.lain.engine.util.math.EVec3
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.World

fun updateBulletsVisual(
    world: World,
    mcWorld: ClientLevel,
) = world.iterate<BulletFire> { _, event ->
    val shoot = event.shoot
    mcWorld.spawnGunSmokeParticle(shoot.start, event.smoke.velocity, shoot.vector, event.smoke.offset)
    val hitResult = raycastBulletEvent(mcWorld, shoot) ?: return@iterate
    val blockPos = hitResult.blockPos
    repeat(5) { mcWorld.addBreakingBlockEffect(blockPos, hitResult.direction) }

    val pos = hitResult.location.engine()
    world.emitEvent(
        BulletHit(
            pos,
            BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)
        )
    )
}

data class BulletHit(val pos: Pos, val bullet: BulletParameters) : Component

fun ClientLevel.spawnGunSmokeParticle(
    pos: EVec3,
    velocity: EVec3,
    direction: EVec3,
    offset: EVec3
) {
    val look = direction.normalize()

    val up = Vec3(0.0f, 1.0f, 0.0f)
    val right = look.cross(up).normalize()
    val realUp = right.cross(look).normalize()

    val localOffset = right.mul(offset.x)
        .add(realUp.mul(offset.y))
        .add(look.mul(offset.z))

    val spawnPos = pos.add(localOffset)
    val spawnVelocity = look.mul(0.35f).add(velocity)

    repeat(1) {
        addParticle(
            ParticleTypes.SMOKE,
            false,
            false,
            spawnPos.x + (Math.random() * 0.1) - 0.05,
            spawnPos.y + (Math.random() * 0.1) - 0.05,
            spawnPos.z + (Math.random() * 0.1) - 0.05,
            spawnVelocity.x + (Math.random() * 0.05) - 0.025,
            spawnVelocity.y + Math.random() * 0.08,
            spawnVelocity.z + (Math.random() * 0.05) - 0.025
        )
    }
}
