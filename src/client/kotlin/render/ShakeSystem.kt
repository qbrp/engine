package org.lain.engine.client.render

import org.lain.engine.client.mc.BulletHit
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Recoil
import org.lain.engine.item.owner
import org.lain.engine.item.recoilSpeed
import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.component.get
import org.lain.engine.util.component.iterate
import org.lain.engine.util.component.remove
import org.lain.engine.world.World

const val SHOOT_SHAKE_DURATION = 0.3f
const val SHOOT_SHAKE_TRAUMA = 0.7f
const val SHOOT_SHAKE_FREQ = 0.5f

fun handleBulletFireShakes(mainPlayer: EnginePlayer, camera: Camera, world: World, items: Collection<EngineItem>) {
    items.forEach { item ->
        val shootTag = item.get<Recoil>() ?: return@forEach
        if (item.owner?.id == mainPlayer.id) {
            camera.shake(
                ShakeEffect(
                    shootTag.bullet.recoilSpeed * SHOOT_SHAKE_TRAUMA,
                    SHOOT_SHAKE_FREQ,
                    SHOOT_SHAKE_DURATION
                )
            )
        }
        item.remove<Recoil>()
    }

    world.iterate<BulletHit> { _, hit ->
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