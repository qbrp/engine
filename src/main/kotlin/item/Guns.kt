package org.lain.engine.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.*
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.world.BulletFireEvent
import org.lain.engine.world.BulletParameters
import org.lain.engine.world.RecoilImpulseEvent
import org.lain.engine.world.ShootGeometry
import org.lain.engine.world.SmokeGeometry
import org.lain.engine.world.World

@Serializable
data class Barrel(
    var bullets: Int,
    val maxBullets: Int,
    val ammunition: ItemId?
) : Component

@Serializable
data class Gun(
    val smoke: ImmutableEVec3? = null,
    val rate: Int = 10,
    val modes: List<FireMode> = listOf(FireMode.SELECTOR, FireMode.SINGLE, FireMode.AUTO)
) : Component

@Serializable
data class GunFireState(
    var cooldown: Int = 0,
    var mode: FireMode = FireMode.SELECTOR,
    var clicked: Boolean = false,
    var triggerPressed: Boolean = false,
    var fired: Boolean = false
) : Component {
    fun copy() = GunFireState(cooldown, mode, clicked, triggerPressed, fired)
}

@Serializable
data class GunMagazines(
    val supports: ItemId,
    var base: Magazine? = null,
) : Component {
    fun copy() = GunMagazines(supports, base?.copy())
}

@Serializable
enum class FireMode {
    SELECTOR, SINGLE, AUTO
}

@Serializable
data class GunDisplay(
    val ammunition: String? = null,
    val magazine: String? = null,
    @SerialName("selector_status") val selectorStatus: Boolean = true,
) : Component

object GunTriggerPressed : Component

object GunModeToggle : Component

data class GunMagazineLoad(val player: EnginePlayer, val magazineItem: EngineItem) : Component

data class GunBarrelLoad(val player: EnginePlayer, val ammoItem: EngineItem) : Component

const val BULLET_FIRE_RADIUS = 64
const val CLICK_SOUND = "click"

private const val ROUND_BARREL_SOUND = "round_barrel"
private const val GUN_TRIGGER_SOUND = "gun_trigger"
private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

context(world: World)
fun EngineItem.isGun() = hasComponent<Gun>()

fun World.tickGunSystem() {
    iterate<GunFireState> { item, fireState ->
        if (item.hasComponent<GunTriggerPressed>() && fireState.mode != FireMode.SELECTOR) {
            if (!fireState.triggerPressed) {
                item.emitPlaySoundEvent(GUN_TRIGGER_SOUND)
                fireState.triggerPressed = true
            }
        } else {
            fireState.triggerPressed = false
            fireState.fired = false
        }

        if (fireState.cooldown >= 0) {
            fireState.cooldown--
        }
    }

    // подача патронов
    iterate<GunMagazines, Barrel, GunFireState> { item, magazines, barrel, fireState ->
        val magazine = magazines.base
        if (magazine != null && barrel.bullets < barrel.maxBullets && magazine.bullets > 0) {
            magazine.bullets--
            barrel.bullets++
            item.markDirty<Barrel>()
            item.markDirty<GunMagazines>()
        }
    }

    // стрельба из патронника
    iterate<Gun, Barrel, HeldBy, GunFireState>() { item, gun, barrel, (shooter), fireState ->
        if (shooter == null) return@iterate
        val canContinueShoot =
            (!fireState.fired || fireState.mode == FireMode.AUTO) && fireState.mode != FireMode.SELECTOR
        if (fireState.triggerPressed && fireState.cooldown < 0 && barrel.bullets > 0 && canContinueShoot) {
            fireState.cooldown = gun.rate
            fireState.fired = true
            barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
            item.emitPlaySoundEvent(GUNFIRE_SOUND)

            val rotationVector = shooter.require<Orientation>().rotationVector
            val start = shooter.eyePos
            val shoot = ShootGeometry(start, rotationVector)
            val parameters = BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)
            emitEvent(
                RecoilImpulseEvent(shooter, shoot, parameters)
            )
            emitEvent(
                BulletFireEvent(
                    shoot,
                    parameters,
                    SmokeGeometry(gun.smoke ?: VEC3_ZERO, shooter.velocity)
                )
            )
        } else {
            if (!fireState.clicked) {
                item.emitPlaySoundEvent(CLICK_SOUND)
                fireState.clicked = true
            }
        }
    }

    iterate<Gun, GunFireState, GunModeToggle>() { item, gun, fireState, _ ->
        val modes = gun.modes
        if (modes.isNotEmpty()) {
            val currentIndex = modes.indexOf(fireState.mode)
            val nextIndex = (currentIndex + 1) % modes.size
            fireState.mode = modes[nextIndex]
            item.emitPlaySoundEvent(SELECTOR_TOGGLE_SOUND)
            item.removeComponent<GunModeToggle>()
            item.markDirty<GunFireState>()
        }
    }

    iterate<GunMagazines, GunFireState, GunMagazineLoad>() { item, magazines, fireState, (player, magazineItem) ->
        item.removeComponent<GunMagazineLoad>()
        if (magazines.base != null) {
            player.serverNarration("<red>Магазин уже вставлен", 40)
            return@iterate
        } else if (magazines.supports != magazineItem.requireComponent<Item>().id) {
            player.serverNarration("<red>Магазин не подходит", 40)
            return@iterate
        }
        magazines.base = magazineItem.getComponent<Magazine>()?.copy() ?: return@iterate
        fireState.clicked = false
        item.emitPlaySoundEvent(ROUND_BARREL_SOUND)
        item.markDirty<GunMagazines>()
        item.markDirty<GunFireState>()
        player.set(DecrementItem(magazineItem))
    }
}

val DEFAULT_WEAPON_MASS = 2f
val DEFAULT_BULLET_MASS = 0.004f
val DEFAULT_BULLET_SPEED = 800f

fun updateBulletsAcoustic(world: World) = world.iterate<BulletFireEvent>() { _, event ->
//    val start = event.shoot.start
//    val affected = filterNearestPlayers(world, start, 8)
//    affected.forEach { player ->
//        // дистанция - 8 блоков
//        val distanceStrength = (64f - player.location.position.squaredDistanceTo(start)).coerceAtLeast(0f) / 8f * 2.5f
//        player.appendTinnitus(
//            Tinnitus(
//                (event.bullet.bulletMass / DEFAULT_BULLET_MASS) * 0.19f, // тиннитус от выстрела пулей стандартной массы = 0.2
//                ((20 * 8) * distanceStrength).toInt()
//            )
//        )
//    }
}
