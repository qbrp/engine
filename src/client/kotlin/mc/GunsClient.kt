package org.lain.engine.client.mc

import net.minecraft.client.world.ClientWorld
import net.minecraft.particle.ParticleTypes
import org.lain.engine.item.BulletFire
import org.lain.engine.item.BulletParameters
import org.lain.engine.item.DEFAULT_BULLET_MASS
import org.lain.engine.item.DEFAULT_BULLET_SPEED
import org.lain.engine.mc.engine
import org.lain.engine.mc.raycastBulletEvent
import org.lain.engine.util.flush
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.World
import org.lain.engine.world.attachBulletDamageDecal
import org.lain.engine.world.events

fun updateBulletsVisual(
    world: World,
    mcWorld: ClientWorld,
) = world.events<BulletFire>().flush { event ->
    val shoot = event.shoot
    mcWorld.spawnGunSmokeParticle(shoot.start, event.smoke.velocity, shoot.vector, event.smoke.offset)
    val hitResult = raycastBulletEvent(mcWorld, shoot) ?: return@flush
    val blockPos = hitResult.blockPos
    repeat(5) { mcWorld.spawnBlockBreakingParticle(blockPos, hitResult.side) }

    val pos = hitResult.pos.engine()
    world.events<BulletHit>().add(
        BulletHit(pos, BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED))
    )
    val dir = hitResult.side.engine()
    world.attachBulletDamageDecal(dir, pos, blockPos.engine())
}

data class BulletHit(val pos: Pos, val bullet: BulletParameters)

fun ClientWorld.spawnGunSmokeParticle(
    pos: Vec3,
    velocity: Vec3,
    direction: Vec3,
    offset: Vec3
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
        addParticleClient(
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
