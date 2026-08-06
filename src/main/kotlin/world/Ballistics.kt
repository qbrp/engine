package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.item.DEFAULT_WEAPON_MASS
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.translateRotation
import org.lain.engine.util.math.EVec3

data class ShootGeometry(val start: EVec3, val vector: EVec3) : Component

data class SmokeGeometry(val offset: EVec3, val velocity: EVec3)

data class BulletParameters(
    val bulletMass: Float,
    val bulletSpeed: Float
)

data class BulletFireEvent(val shoot: ShootGeometry, val bullet: BulletParameters, val smoke: SmokeGeometry) : Component

// Recoil

val BulletParameters.recoilSpeed get() = bulletMass * bulletSpeed / DEFAULT_WEAPON_MASS

data class RecoilImpulseEvent(
    val player: EnginePlayer,
    val shoot: ShootGeometry,
    val bullet: BulletParameters
) : Component

fun World.tickRecoilSystem() = iterate<RecoilImpulseEvent> { item, (owner, shoot, bullet) ->
    owner.translateRotation(pitch = -(bullet.recoilSpeed * 3f))
}