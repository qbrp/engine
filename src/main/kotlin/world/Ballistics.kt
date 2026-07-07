package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.item.DEFAULT_WEAPON_MASS
import org.lain.engine.item.HoldsBy
import org.lain.engine.item.Item
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

data class RecoilImpulse(
    val shoot: ShootGeometry,
    val bullet: BulletParameters
) : Component

fun World.tickRecoilSystem(remove: Boolean = true) = iterate<Item, RecoilImpulse, HoldsBy> { item, _, recoil, (owner) ->
    owner.translateRotation(pitch = -(recoil.bullet.recoilSpeed * 3f))
    if (remove) { item.removeComponent<RecoilImpulse>() }
}