package org.lain.engine.client.render

import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.client.mc.BulletHit
import org.lain.engine.item.HoldsBy
import org.lain.engine.item.Recoil
import org.lain.engine.item.recoilSpeed
import org.lain.engine.player.EnginePlayer
import org.lain.engine.world.World

const val SHOOT_SHAKE_DURATION = 0.3f
const val SHOOT_SHAKE_TRAUMA = 0.7f
const val SHOOT_SHAKE_FREQ = 0.5f

fun World.updateShootShakeSystem(mainPlayer: EnginePlayer, camera: Camera) {
    iterate<Recoil, HoldsBy> { item, recoil, (owner) ->
        if (owner.id == mainPlayer.id) {
            camera.shake(
                ShakeEffect(
                    recoil.bullet.recoilSpeed * SHOOT_SHAKE_TRAUMA,
                    SHOOT_SHAKE_FREQ,
                    SHOOT_SHAKE_DURATION
                )
            )
        }
        item.removeComponent<Recoil>()
    }

    iterate<BulletHit> { _, hit ->
        camera.shake(
            ShakeEffect(
                hit.bullet.bulletMass * 10f,
                SHOOT_SHAKE_FREQ * 0.5f,
                SHOOT_SHAKE_DURATION,
                ShakeLocation(
                    hit.pos, 8f
                )
            )
        )
    }
}