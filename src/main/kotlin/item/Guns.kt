package org.lain.engine.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.player.*
import org.lain.engine.server.ItemSynchronizable
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.*
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.events
import org.lain.engine.world.world

@Serializable
data class Barrel(var bullets: Int, val maxBullets: Int)

@Serializable
data class Gun(
    val barrel: Barrel = Barrel(0, 2),
    var clicked: Boolean = false,
    val ammunition: ItemId?,
    val smoke: ImmutableVec3? = null,
    val rate: Int = 10,
    var fireTime: Int = 0,
    var mode: FireMode = FireMode.SELECTOR,
    val modes: List<FireMode> = listOf(FireMode.SELECTOR, FireMode.SINGLE, FireMode.AUTO),
) : ItemComponent, ItemSynchronizable {
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
fun EngineItem.gunAmmoConsumeCount(item: EngineItem): Int {
    val gun = this.get<Gun>()
    if (gun == null || item.id != gun.ammunition) return 0
    val count = item.count
    val barrel = gun.barrel
    return count.coerceAtMost(barrel.maxBullets - barrel.bullets)
}

data class GunShoot(val start: Vec3, val vector: Vec3) : Component

data class BulletFire(val shoot: GunShoot, val bullet: BulletParameters, val smoke: Smoke) : Component

data class Smoke(val offset: Vec3, val velocity: Vec3)

data class Recoil(
    val shoot: GunShoot,
    val bullet: BulletParameters
) : Component

data class BulletParameters(
    val bulletMass: Float,
    val bulletSpeed: Float
)

const val BULLET_FIRE_RADIUS = 64

private const val ROUND_BARREL_SOUND = "round_barrel"
private const val CLICK_EMPTY_SOUND = "click_empty"
private const val GUN_TRIGGER_SOUND = "gun_trigger"
private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

private val GUN_SHOOT_VERB = ItemVerb(
    VerbId("shoot"),
    "Нажать на спусковой крючок",
)

private val GUN_TOGGLE_MODE_VERB = ItemVerb(
    VerbId("gun_toggle_mode"),
    "Сменить режим огня",
)

private val GUN_BARREL_AMMO_LOAD_VERB = ItemVerb(
    VerbId("barrel_ammo_load"),
    "Загрузить патроны в патронник",
)

fun tickInventoryGun(items: Collection<EngineItem>) {
    for (item in items) {
        val gun = item.get<Gun>() ?: continue
        if (gun.fireTime > 0) {
            gun.fireTime--
        }
    }
}

val EngineItem?.isGun
    get() = this?.has<Gun>() == true

fun appendGunVerbs(player: EnginePlayer) {
    player.handle<VerbLookup>() {
        if (!handItem.isGun && !(slotClick?.item?.isGun ?: false)) return@handle
        forAction<InputAction.Attack>(GUN_SHOOT_VERB)
        forAction<InputAction.Base>(GUN_TOGGLE_MODE_VERB)
        forAction<InputAction.SlotClick> { action ->
            GUN_BARREL_AMMO_LOAD_VERB.takeIf {
                action.item.gunAmmoConsumeCount(action.cursorItem) > 0
            }
        }
    }
}

fun handleGunInteractions(player: EnginePlayer, isClient: Boolean = false) {
    player.handleInteraction(GUN_SHOOT_VERB) {
        val gun = handItem!!.require<Gun>()
        val barrel = gun.barrel
        if (gun.mode == FireMode.SELECTOR || gun.fireTime > 0) return@handleInteraction

        if (gun.fireTime == 0) {
            emitItemInteractionSoundEvent(handItem, GUN_TRIGGER_SOUND)
            gun.fireTime = gun.rate
        }

        if (barrel.bullets > 0) {
            barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
            println("Патроны: ${barrel.bullets}")
            emitItemInteractionSoundEvent(handItem, GUNFIRE_SOUND)

            val rotationVector = player.require<Orientation>().rotationVector
            val start = player.eyePos
            val shoot = GunShoot(start, rotationVector)
            val parameters = BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)
            handItem.set(Recoil(shoot, parameters))
            handItem.world.events<BulletFire>() += BulletFire(
                shoot,
                parameters,
                Smoke(gun.smoke ?: VEC3_ZERO, player.velocity)
            )

            if (gun.mode != FireMode.AUTO) {
                player.finishInteraction()
            }
        } else {
            if (!gun.clicked) {
                emitItemInteractionSoundEvent(handItem, CLICK_EMPTY_SOUND)
                gun.clicked = true
            }
            player.finishInteraction()
        }
    }

    player.handleInteraction(GUN_TOGGLE_MODE_VERB) {
        val gun = handItem!!.require<Gun>()
        val modes = gun.modes

        if (modes.isNotEmpty()) {
            val currentIndex = modes.indexOf(gun.mode)
            val nextIndex = (currentIndex + 1) % modes.size
            gun.mode = modes[nextIndex]
            emitItemInteractionSoundEvent(handItem, SELECTOR_TOGGLE_SOUND)
            val text = if (isClient) "Client" else "Server"
            println("[$text] Режим стрельбы: ${gun.mode}")
        }

        player.finishInteraction()
    }

    player.handleInteraction(GUN_BARREL_AMMO_LOAD_VERB) {
        val gun = slotAction.item.require<Gun>()
        val barrel = gun.barrel

        val cursorItem = slotAction.cursorItem
        val slotItem = slotAction.item
        val count = slotAction.item.gunAmmoConsumeCount(slotAction.cursorItem)
        if (count > 0) {
            barrel.bullets = (barrel.bullets + count).coerceAtMost(barrel.maxBullets)
            gun.clicked = false
            emitItemInteractionSoundEvent(slotItem, ROUND_BARREL_SOUND)
            player.set(DestroyItemSignal(cursorItem.uuid, count))
        }
        player.finishInteraction()
    }
}

val DEFAULT_WEAPON_MASS = 2f
val DEFAULT_BULLET_MASS = 0.004f
val DEFAULT_BULLET_SPEED = 800f

val BulletParameters.recoilSpeed get() = bulletMass * bulletSpeed / DEFAULT_WEAPON_MASS

fun handleItemRecoil(
    player: EnginePlayer,
    items: Collection<EngineItem> = player.items,
    remove: Boolean = true
) {
    items.forEach { item ->
        val shootTag = item.get<Recoil>() ?: return@forEach
        player.translateRotation(pitch = -(shootTag.bullet.recoilSpeed * 3f))
        if (remove) {
            item.removeComponent(shootTag)
        }
    }
}