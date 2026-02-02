package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes

fun spawnGunSmokeParticle(player: PlayerEntity) {
    val lookVector = player.rotationVector.normalize()
    val pos = player.eyePos.add(lookVector.multiply(0.5))
    repeat(6) { i ->
        player.entityWorld.addParticleClient(
            ParticleTypes.SMOKE,
            false,
            false,
            pos.getX() + (Math.random() * 0.2) - 0.1,
            pos.getY() + (Math.random() * 0.2) - 0.1,
            pos.getZ() + (Math.random() * 0.2) - 0.1,
            (Math.random() * 0.05) - 0.025,
            0.09 + Math.random() * 0.08,
            (Math.random() * 0.05) - 0.025
        )
    }
}