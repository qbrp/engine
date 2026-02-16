package org.lain.engine.client.render

import org.lain.engine.client.mc.BulletHit
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ShootTag
import org.lain.engine.item.recoilSpeed
import org.lain.engine.util.flush
import org.lain.engine.util.get
import org.lain.engine.world.World
import org.lain.engine.world.events

const val SHOOT_SHAKE_DURATION = 0.3f
const val SHOOT_SHAKE_TRAUMA = 0.7f
const val SHOOT_SHAKE_FREQ = 0.5f

fun handleBulletFireShakes(camera: Camera, world: World, items: Collection<EngineItem>) {
    items.forEach { item ->
        val shootTag = item.get<ShootTag>() ?: return@forEach
        camera.shake(
            ShakeEffect(
                shootTag.bullet.recoilSpeed * SHOOT_SHAKE_TRAUMA,
                SHOOT_SHAKE_FREQ,
                SHOOT_SHAKE_DURATION
            )
        )
    }

    world.events<BulletHit>().flush { hit ->
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