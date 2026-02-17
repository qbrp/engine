package org.lain.engine.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.items
import org.lain.engine.player.translateRotation
import org.lain.engine.util.Component
import org.lain.engine.util.get
import org.lain.engine.util.handle
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.set
import org.lain.engine.world.World
import org.lain.engine.world.events
import org.lain.engine.world.world

@Serializable
data class Barrel(var bullets: Int, val maxBullets: Int)

@Serializable
data class Gun(
    val barrel: Barrel = Barrel(0, 2),
    var selector: Boolean = true,
    var clicked: Boolean = false,
    val ammunition: ItemId?,
) : Component {
    fun copy(): Gun {
        return Gun(Barrel(barrel.bullets, barrel.maxBullets), selector, clicked, ammunition)
    }
}

@Serializable
data class GunDisplay(
    val ammunition: String? = null,
    @SerialName("selector_status") val selectorStatus: Boolean = true,
) : Component

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
    data class BarrelAmmoLoad(val count: Int) : GunEvent()
    data class Shoot(val shoot: GunShoot) : GunEvent()
    object SelectorToggle : GunEvent()
}

data class GunShoot(val start: Vec3, val vector: Vec3) : Component

data class ShootTag(
    val shoot: GunShoot,
    val bullet: BulletParameters
) : Component

data class BulletParameters(
    val bulletMass: Float,
    val bulletSpeed: Float
)

fun EngineItem.setGunEvent(event: GunEvent) {
    this.set(GunEventComponent(event))
}

const val BULLET_FIRE_RADIUS = 64

private const val ROUND_BARREL_SOUND = "round_barrel"
private const val CLICK_EMPTY_SOUND = "click_empty"
private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

// Вызывается на клиенте (для предсказания) и сервере
fun updateGunState(items: Collection<EngineItem>) {
    for (item in items) { // Кейс 1: загрузка боеприпасов в оружие
        val gun = item.get<Gun>() ?: continue
        val barrel = gun.barrel
        item.handle<GunEventComponent> {
            when(val event = this.event) {
                is GunEvent.BarrelAmmoLoad -> {
                    barrel.bullets = (barrel.bullets + event.count).coerceAtMost(barrel.maxBullets)
                    item.emitPlaySoundEvent(ROUND_BARREL_SOUND, EngineSoundCategory.NEUTRAL)
                    gun.clicked = false
                }
                is GunEvent.Shoot -> {
                    if (!gun.selector) {
                        val shoot = event.shoot
                        if (barrel.bullets > 0) {
                            barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
                            item.emitPlaySoundEvent(GUNFIRE_SOUND, EngineSoundCategory.NEUTRAL)
                            item.set(ShootTag(shoot, BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)))
                            item.world.events<GunShoot>() += shoot
                        } else if (!gun.clicked) {
                            item.emitPlaySoundEvent(CLICK_EMPTY_SOUND, EngineSoundCategory.NEUTRAL)
                            gun.clicked = true
                        }
                    }
                }
                is GunEvent.SelectorToggle -> {
                    gun.selector = !gun.selector
                    item.emitPlaySoundEvent(SELECTOR_TOGGLE_SOUND, EngineSoundCategory.NEUTRAL)
                }
            }
            item.removeComponent(this)
        }
    }
}

val DEFAULT_WEAPON_MASS = 2f
val DEFAULT_BULLET_MASS = 0.004f
val DEFAULT_BULLET_SPEED = 800f

val BulletParameters.recoilSpeed get() = bulletMass * bulletSpeed / DEFAULT_WEAPON_MASS

fun handleGunShotTags(
    player: EnginePlayer,
    items: Collection<EngineItem> = player.items,
    world: World = player.world,
) {
    items.forEach { item ->
        val shootTag = item.get<ShootTag>() ?: return@forEach
        player.translateRotation(pitch = -(shootTag.bullet.recoilSpeed * 3f))
        item.removeComponent(shootTag)
    }
}