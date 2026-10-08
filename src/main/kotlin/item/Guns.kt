package org.lain.engine.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.*
import org.lain.engine.player.interaction.Hand
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.world.BulletFireEvent
import org.lain.engine.world.BulletParameters
import org.lain.engine.world.RecoilEvent
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
    val modes: List<FireMode> = listOf(FireMode.SAFETY, FireMode.SINGLE, FireMode.AUTO)
) : Component

@Serializable
data class GunFireState(
    var cooldown: Int = 0,
    var mode: FireMode = FireMode.SAFETY,
    var clicked: Boolean = false,
    var triggerPressed: Boolean = false,
    var triggerSoundPlayed: Boolean = false,
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
enum class FireMode(val displayName: String) {
    SAFETY("<red>Предохранитель"), SINGLE("<green>Одиночный"), AUTO("<yellow>Автоматический");
}

@Serializable
data class GunDisplay(
    val ammunition: String? = null,
    val magazine: String? = null,
    @SerialName("selector_status") val selectorStatus: Boolean = true,
) : Component

object HoldsGunTrigger : Component

data class GunModeToggle(val player: EnginePlayer) : Component

data class GunMagazineLoad(val player: EnginePlayer, val magazineItem: EngineItem) : Component

data class GunBarrelLoad(val player: EnginePlayer, val ammoItem: EngineItem) : Component

data class GunMagazineTakeOff(val player: EnginePlayer) : Component

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
        fireState.triggerPressed = false

        if (fireState.cooldown >= 0) {
            fireState.cooldown--
        }
    }

    iterate<Hand, HoldsGunTrigger>() { interactor, hand, _ ->
        val item = hand.item
        val fireState = item?.getComponent<GunFireState>() ?: return@iterate
        fireState.triggerPressed = true
    }

    iterate<GunFireState>() { item, fireState ->
        if (fireState.triggerPressed) {
            if (!fireState.triggerSoundPlayed) {
                item.emitPlaySoundEvent(GUN_TRIGGER_SOUND)
                fireState.triggerSoundPlayed = true
            }
        } else {
            fireState.triggerSoundPlayed = false
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
            (!fireState.fired || fireState.mode == FireMode.AUTO) && fireState.mode != FireMode.SAFETY
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
                RecoilEvent(shooter, shoot, parameters)
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

    iterate<Gun, GunFireState, GunModeToggle>() { item, gun, fireState, (by) ->
        val modes = gun.modes
        if (modes.isNotEmpty()) {
            val currentIndex = modes.indexOf(fireState.mode)
            val nextIndex = (currentIndex + 1) % modes.size
            fireState.mode = modes[nextIndex]
            by.narration(fireState.mode.displayName, 40)
            item.emitPlaySoundEvent(SELECTOR_TOGGLE_SOUND)
            item.removeComponent<GunModeToggle>()
            item.markDirty<GunFireState>()
        }
    }

    iterate<GunMagazines, GunFireState, GunMagazineLoad>() { item, magazines, fireState, (player, magazineItem) ->
        item.removeComponent<GunMagazineLoad>()
        if (magazines.base != null) {
            player.narration("<red>Магазин уже вставлен", 40)
            return@iterate
        } else if (magazines.supports != magazineItem.requireComponent<Item>().id) {
            player.narration("<red>Магазин не подходит", 40)
            return@iterate
        }
        magazines.base = magazineItem.getComponent<Magazine>()?.copy() ?: return@iterate
        fireState.clicked = false
        item.emitPlaySoundEvent(ROUND_BARREL_SOUND)
        item.markDirty<GunMagazines>()
        item.markDirty<GunFireState>()
        magazineItem.setComponent(DecrementItem(magazineItem))
    }

    iterate<GunMagazines, GunMagazineTakeOff>() { gun, magazines, (player) ->
        gun.removeComponent<GunMagazineTakeOff>()
        val prefabId = magazines.supports

        val taken = magazines.base ?: return@iterate
        magazines.base = null

        val prefab = simulation.namespacedStorage.items[prefabId] ?: run {
            player.narration(
                "<red>Магазин не может быть выдан из-за несовпадения идентификаторов. Это непредвиденная ошибка, сообщите о ней разработчику или администраторам сервера",
                200
            )
            return@iterate
        }

        val magazine = createItem(prefab, server?.entityCoordinator, this@tickGunSystem)
        magazine.setComponent(taken)
        emitEvent(
            GiveItemEvent(player, magazine)
        )
    }
}

val DEFAULT_WEAPON_MASS = 2f
val DEFAULT_BULLET_MASS = 0.004f
val DEFAULT_BULLET_SPEED = 800f