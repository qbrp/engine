package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.player.DestroyItemSignal
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerUpdate
import org.lain.engine.player.markUpdate
import org.lain.engine.util.Component
import org.lain.engine.util.applyIfExists
import org.lain.engine.util.get
import org.lain.engine.util.remove
import org.lain.engine.util.set
import org.lain.engine.world.emitPlaySoundEvent
import org.lain.engine.world.world

@Serializable
data class Barrel(var bullets: Int, val maxBullets: Int)

@Serializable
data class Gun(
    val barrel: Barrel = Barrel(0, 2),
    var selector: Boolean = true,
    val ammunition: ItemId
) : Component {
    fun copy(): Gun {
        return Gun(Barrel(barrel.bullets, barrel.maxBullets), selector, ammunition)
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

private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

// Вызывается на клиенте (для предсказания) и сервере
fun updateGunState(items: Set<EngineItem>) {
    for (item in items) { // Кейс 1: загрузка боеприпасов в оружие
        val gun = item.get<Gun>() ?: continue
        val barrel = gun.barrel
        item.applyIfExists<GunEventComponent> {
            when(val event = this.event) {
                is GunEvent.BarrelAmmoLoad -> {
                    barrel.bullets = (barrel.bullets + event.count).coerceAtMost(barrel.maxBullets)
                    event.loader.markUpdate(PlayerUpdate.GunBarrelBullets(item.uuid, barrel.bullets))
                }
                is GunEvent.Shoot -> {
                    if (barrel.bullets > 0 && !gun.selector) {
                        barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
                        item.emitPlaySoundEvent(GUNFIRE_SOUND, EngineSoundCategory.NEUTRAL)
                        event.shooter.markUpdate(PlayerUpdate.GunBarrelBullets(item.uuid, barrel.bullets))
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