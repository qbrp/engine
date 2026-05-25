package org.lain.engine.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.*
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.math.EVec3
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.util.math.filterNearestPlayers
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
) : ItemComponent

/**
 * @return Уменьшение количества принимаемого как патрон предмета для предмета-оружия
 */
fun EngineItem.gunAmmoConsumeCount(world: World, item: EngineItem): Int = with(world) {
    val gun = item.getComponent<Gun>()
    if (gun == null || item.getComponent<Item>()?.id != gun.ammunition) return 0
    val count = item.getComponent<Count>()?.value ?: 1
    val barrel = gun.barrel
    return count.coerceAtMost(barrel.maxBullets - barrel.bullets)
}

data class GunShoot(val start: EVec3, val vector: EVec3) : Component

data class BulletFire(val shoot: GunShoot, val bullet: BulletParameters, val smoke: Smoke) : Component

data class Smoke(val offset: EVec3, val velocity: EVec3)

data class Recoil(
    val shoot: GunShoot,
    val bullet: BulletParameters
) : Component

data class BulletParameters(
    val bulletMass: Float,
    val bulletSpeed: Float
)

const val BULLET_FIRE_RADIUS = 64
const val CLICK_SOUND = "click"

private const val ROUND_BARREL_SOUND = "round_barrel"
private const val GUN_TRIGGER_SOUND = "gun_trigger"
private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

private val GUN_SHOOT_VERB = VerbType(
    "shoot",
    "Нажать на спусковой крючок",
)

private val GUN_TOGGLE_MODE_VERB = VerbType(
    "gun_toggle_mode",
    "Сменить режим огня",
)

private val GUN_BARREL_AMMO_LOAD_VERB = VerbType(
    "barrel_ammo_load",
    "Загрузить патроны в патронник",
)

fun World.updateFireTimeSystem() = iterate<Gun> { item, gun ->
    if (gun.fireTime > 0) {
        gun.fireTime--
    }
}

context(world: World)
fun EngineItem.isGun() = hasComponent<Gun>()

fun World.appendGunVerbs(player: EnginePlayer) {
    player.handle<VerbLookup>() {
        if (handItem?.isGun() != true && !(slotClick?.item?.isGun() ?: false)) return@handle
        forAction<InputAction.Attack>(GUN_SHOOT_VERB)
        forAction<InputAction.Base>(GUN_TOGGLE_MODE_VERB)
        forAction<InputAction.SlotClick> { action ->
            GUN_BARREL_AMMO_LOAD_VERB.takeIf {
                action.item.gunAmmoConsumeCount(this@appendGunVerbs, action.cursorItem) > 0
            }
        }
    }
}

context(interaction: InteractionComponent)
fun World.handleGunInteractions(player: EnginePlayer) {
    player.handleInteraction(GUN_SHOOT_VERB) {
        val gun = handItem!!.requireComponent<Gun>()
        val barrel = gun.barrel
        if (gun.mode == FireMode.SELECTOR || gun.fireTime > 0) return@handleInteraction

        if (gun.fireTime == 0) {
            emitItemInteractionSoundEvent(handItem, GUN_TRIGGER_SOUND)
            gun.fireTime = gun.rate
        }

        fun finish() {
            complete()
            handItem.markDirty<Gun>()
        }

        if (occupied && !player.actionsSimilar()) {
            finish()
        }

        if (barrel.bullets > 0) {
            barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
            emitItemInteractionSoundEvent(handItem, GUNFIRE_SOUND)

            val rotationVector = player.require<Orientation>().rotationVector
            val start = player.eyePos
            val shoot = GunShoot(start, rotationVector)
            val parameters = BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)
            handItem.setComponent(Recoil(shoot, parameters))
            emitEvent(
                BulletFire(
                    shoot,
                    parameters,
                    Smoke(gun.smoke ?: VEC3_ZERO, player.velocity)
                )
            )

            if (gun.mode != FireMode.AUTO) {
                finish()
            } else {
                occupy()
            }
        } else {
            if (!gun.clicked) {
                emitItemInteractionSoundEvent(handItem, CLICK_SOUND)
                gun.clicked = true
            }
            finish()
        }
    }

    player.handleInteraction(GUN_TOGGLE_MODE_VERB) {
        val gun = handItem!!.requireComponent<Gun>()
        val modes = gun.modes

        if (modes.isNotEmpty()) {
            val currentIndex = modes.indexOf(gun.mode)
            val nextIndex = (currentIndex + 1) % modes.size
            gun.mode = modes[nextIndex]
            emitItemInteractionSoundEvent(handItem, SELECTOR_TOGGLE_SOUND)
            handItem.markDirty<Gun>()
        }

        complete()
    }

    player.handleInteraction(GUN_BARREL_AMMO_LOAD_VERB) {
        val gun = slotAction.item.requireComponent<Gun>()
        val barrel = gun.barrel

        val cursorItem = slotAction.cursorItem
        val slotItem = slotAction.item
        val count = slotAction.item.gunAmmoConsumeCount(this@handleGunInteractions, slotAction.cursorItem)
        if (count > 0) {
            barrel.bullets = (barrel.bullets + count).coerceAtMost(barrel.maxBullets)
            gun.clicked = false
            emitItemInteractionSoundEvent(slotItem, ROUND_BARREL_SOUND)
            player.set(DestroyItemSignal(cursorItem, count))
            slotItem.markDirty<Gun>()
        }
        complete()
    }
}

val DEFAULT_WEAPON_MASS = 2f
val DEFAULT_BULLET_MASS = 0.004f
val DEFAULT_BULLET_SPEED = 800f

val BulletParameters.recoilSpeed get() = bulletMass * bulletSpeed / DEFAULT_WEAPON_MASS

fun World.updateRecoilSystem(remove: Boolean = true) = iterate<Item, Recoil, HoldsBy> { item, _, recoil, (owner) ->
    owner.translateRotation(pitch = -(recoil.bullet.recoilSpeed * 3f))
    if (remove) { item.removeComponent<Recoil>() }
}

fun updateBulletsAcoustic(world: World) = world.iterate<BulletFire>() { _, event ->
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