package org.lain.engine.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.cyberia.ecs.set
import org.lain.engine.player.*
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.world.BulletFireEvent
import org.lain.engine.world.BulletParameters
import org.lain.engine.world.RecoilImpulse
import org.lain.engine.world.ShootGeometry
import org.lain.engine.world.SmokeGeometry
import org.lain.engine.world.World
import org.lain.engine.world.pos

@Serializable
data class Barrel(var bullets: Int, val maxBullets: Int)

@Serializable
data class Gun(
    val barrel: Barrel = Barrel(0, 2),
    var clicked: Boolean = false,
    val ammunition: ItemId?,
    val smoke: ImmutableEVec3? = null,
    val rate: Int = 10,
    var fireTime: Int = 0,
    var mode: FireMode = FireMode.SELECTOR,
    val modes: List<FireMode> = listOf(FireMode.SELECTOR, FireMode.SINGLE, FireMode.AUTO),
) : Component {
    fun copy(): Gun {
        return Gun(
            Barrel(barrel.bullets, barrel.maxBullets),
            clicked, ammunition, smoke, rate, fireTime, mode, modes
        )
    }
}

@Serializable
enum class FireMode {
    SELECTOR, SINGLE, AUTO
}

@Serializable
data class GunDisplay(
    val ammunition: String? = null,
    @SerialName("selector_status") val selectorStatus: Boolean = true,
) : Component

/**
 * @return Уменьшение количества принимаемого как патрон предмета для предмета-оружия
 */
fun computeGunAmmoConsumeCount(world: World, item: EngineItem, gun: Gun? = null): Int = with(world) {
    val gun = gun ?: item.getComponent<Gun>()
    if (gun == null || item.getComponent<Item>()?.id != gun.ammunition) return 0
    val count = item.getComponent<Count>()?.value ?: 1
    val barrel = gun.barrel
    return count.coerceAtMost(barrel.maxBullets - barrel.bullets)
}
object GunTriggerPress : Component

object GunModeToggle : Component

data class GunBarrelLoad(val player: EnginePlayer, val ammoItem: EngineItem) : Component

const val BULLET_FIRE_RADIUS = 64
const val CLICK_SOUND = "click"

private const val ROUND_BARREL_SOUND = "round_barrel"
private const val GUN_TRIGGER_SOUND = "gun_trigger"
private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

fun World.tickFireTimeSystem() = iterate<Gun> { item, gun ->
    if (gun.fireTime > 0) {
        gun.fireTime--
    }
}

context(world: World)
fun EngineItem.isGun() = hasComponent<Gun>()

fun World.tickGunSystem() {
    iterate<Gun, HoldsBy, GunTriggerPress>() { item, gun, (shooter), _ ->
        val barrel = gun.barrel

        if (gun.fireTime == 0) {
            item.emitPlaySoundEvent(GUN_TRIGGER_SOUND)
            gun.fireTime = gun.rate
        }

        if (barrel.bullets > 0 && gun.fireTime > 0) {
            barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
            item.emitPlaySoundEvent(GUNFIRE_SOUND)

            val rotationVector = shooter.require<Orientation>().rotationVector
            val start = shooter.eyePos
            val shoot = ShootGeometry(start, rotationVector)
            val parameters = BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)
            item.setComponent(RecoilImpulse(shoot, parameters))
            emitEvent(
                BulletFireEvent(
                    shoot,
                    parameters,
                    SmokeGeometry(gun.smoke ?: VEC3_ZERO, shooter.velocity)
                )
            )
        } else {
            if (!gun.clicked) {
                item.emitPlaySoundEvent(CLICK_SOUND)
                gun.clicked = true
            }
        }

        item.removeComponent<GunTriggerPress>()
    }

    iterate<Gun, GunModeToggle>() { item, gun, _ ->
        val modes = gun.modes
        if (modes.isNotEmpty()) {
            val currentIndex = modes.indexOf(gun.mode)
            val nextIndex = (currentIndex + 1) % modes.size
            gun.mode = modes[nextIndex]
            item.emitPlaySoundEvent(SELECTOR_TOGGLE_SOUND)
            item.removeComponent<GunModeToggle>()
            item.markDirty<Gun>()
        }
    }

    iterate<Gun, GunBarrelLoad>() { item, gun, (player, ammoItem) ->
        val barrel = gun.barrel
        val ammo = computeGunAmmoConsumeCount(this@tickGunSystem, item, gun)
        if (ammo > 0) {
            barrel.bullets = (barrel.bullets + ammo).coerceAtMost(barrel.maxBullets)
            gun.clicked = false
            item.emitPlaySoundEvent(ROUND_BARREL_SOUND)
            player.set(DestroyItemSignal(ammoItem, ammo))
            item.removeComponent<GunBarrelLoad>()
            item.markDirty<Gun>()
        }
    }
}

val DEFAULT_WEAPON_MASS = 2f
val DEFAULT_BULLET_MASS = 0.004f
val DEFAULT_BULLET_SPEED = 800f

fun updateBulletsAcoustic(world: World) = world.iterate<BulletFireEvent>() { _, event ->
    val start = event.shoot.start
    val affected = filterNearestPlayers(world, start, 8)
    affected.forEach { player ->
        // дистанция - 8 блоков
        val distanceStrength = (64f - player.pos.squaredDistanceTo(start)).coerceAtLeast(0f) / 8f * 2.5f
        player.appendTinnitus(
            Tinnitus(
                (event.bullet.bulletMass / DEFAULT_BULLET_MASS) * 0.19f, // тиннитус от выстрела пулей стандартной массы = 0.2
                ((20 * 8) * distanceStrength).toInt()
            )
        )
    }
}