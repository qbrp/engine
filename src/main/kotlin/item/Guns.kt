package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.player.*
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.*
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.World
import org.lain.engine.world.world
import java.util.*

@Serializable
data class Barrel(var bullets: Int, val maxBullets: Int)

@Serializable
data class Gun(
    val barrel: Barrel = Barrel(0, 2),
    var selector: Boolean = true,
    var clicked: Boolean = false,
    val ammunition: ItemId
) : Component {
    fun copy(): Gun {
        return Gun(Barrel(barrel.bullets, barrel.maxBullets), selector, clicked, ammunition)
    }
}

@Serializable
data class GunDisplay(val ammunition: String) : Component

/**
 * @return Уменьшение количества принимаемого как патрон предмета для предмета-оружия
 */
fun EngineItem.gunAmmoConsumeCount(item: EngineItem): Int {
    val gun = this.get<Gun>()
    if (gun == null || item.id != gun.ammunition) return 0
    val count = item.count
    val barrel = gun.barrel
    return count.coerceAtMost(barrel.maxBullets - barrel.bullets)
}

data class GunEventComponent(val event: GunEvent) : Component

sealed class GunEvent {
    data class BarrelAmmoLoad(val count: Int, val loader: EnginePlayer) : GunEvent()
    data class Shoot(val shooter: EnginePlayer) : GunEvent()
    data class SelectorToggle(val player: EnginePlayer) : GunEvent()
}

fun EngineItem.setGunEvent(event: GunEvent) {
    this.set(GunEventComponent(event))
}


data class WorldGunEvents(val bullet: Queue<BulletFireEvent> = LinkedList()) : Component

fun World.emitBulletFireEvent(start: Vec3, vector: Vec3, exclude: EnginePlayer?) {
    this.require<WorldGunEvents>().bullet += BulletFireEvent(start, vector, exclude)
}

data class BulletFireEvent(val start: Vec3, val vector: Vec3, val shooter: EnginePlayer?)

const val BULLET_FIRE_RADIUS = 64

private const val ROUND_BARREL = "round_barrel"
private const val ROUND_BARREL_FULL = "round_barrel_full"
private const val CLICK_EMPTY_SOUND = "click_empty"
private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

// Вызывается на клиенте (для предсказания) и сервере
fun updateGunState(items: Set<EngineItem>, client: Boolean = false) {
    for (item in items) { // Кейс 1: загрузка боеприпасов в оружие
        val gun = item.get<Gun>() ?: continue
        val barrel = gun.barrel
        item.handle<GunEventComponent> {
            when(val event = this.event) {
                is GunEvent.BarrelAmmoLoad -> {
                    barrel.bullets = (barrel.bullets + event.count).coerceAtMost(barrel.maxBullets)
                    event.loader.markUpdate(PlayerUpdate.GunBarrelBullets(item.uuid, barrel.bullets))
                    item.emitPlaySoundEvent(ROUND_BARREL, EngineSoundCategory.NEUTRAL)
                    gun.clicked = false
                }
                is GunEvent.Shoot -> {
                    val shooter = event.shooter
                    if (!gun.selector) {
                        if (barrel.bullets > 0) {
                            barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
                            item.emitPlaySoundEvent(GUNFIRE_SOUND, EngineSoundCategory.NEUTRAL)
                            shooter.translateRotation(pitch = -5f)
                            shooter.markUpdate(PlayerUpdate.GunBarrelBullets(item.uuid, barrel.bullets))
                            // FIXME: Антипаттерн, сделать вместо этого обработку по GunFireEvent
                            if (client) {
                                shooter.shake(0.3f)
                            }

                            val rotationVector = event.shooter.require<Orientation>().rotationVector
                            val start = event.shooter.eyePos
                            shooter.world.emitBulletFireEvent(start, rotationVector, shooter)
                        } else if (!gun.clicked) {
                            item.emitPlaySoundEvent(CLICK_EMPTY_SOUND, EngineSoundCategory.NEUTRAL)
                            gun.clicked = true
                        }
                    }
                }
                is GunEvent.SelectorToggle -> {
                    gun.selector = !gun.selector
                    item.emitPlaySoundEvent(SELECTOR_TOGGLE_SOUND, EngineSoundCategory.NEUTRAL)
                    event.player.markUpdate(PlayerUpdate.GunSelector(item.uuid, gun.selector))
                }
            }
            item.removeComponent(this)
        }
    }
}

fun broadcastBulletEvents(handler: ServerHandler, world: World) = world.require<WorldGunEvents>().bullet.flush {
    handler.onBulletEvent(world, it)
}