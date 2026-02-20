package org.lain.engine.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.engine.player.*
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
    var selector: Boolean = true,
    var clicked: Boolean = false,
    val ammunition: ItemId?,
    val smoke: ImmutableVec3? = null,
    val rate: Int = 10
) : ItemComponent {
    fun copy(): Gun {
        return Gun(Barrel(barrel.bullets, barrel.maxBullets), selector, clicked, ammunition, smoke, rate)
    }
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
private const val GUNFIRE_SOUND = "gunfire"
private const val SELECTOR_TOGGLE_SOUND = "selector"

private val GUN_SHOOT_VERB = ItemVerb(
    VerbId("shoot"),
    "Одиночный выстрел",
)

private val GUN_SELECTOR_TOGGLE_VERB = ItemVerb(
    VerbId("selector_toggle"),
    "Сменить предохранитель",
)

private val GUN_BARREL_AMMO_LOAD_VERB = VerbId("barrel_ammo_load")

fun appendGunVerbs(player: EnginePlayer) {
    val lookup = player.get<VerbLookup>() ?: return
    val handItem = player.handItem
    val verbs = lookup.verbs
    for (action in lookup.actions) {
        if (action is InputAction.Attack) {
            if (handItem?.has<Gun>() == true) {
                lookup.verbs += VerbVariant(GUN_SHOOT_VERB, action)
            }
        } else if (action is InputAction.Base) {
            if (handItem?.has<Gun>() == true) {
                lookup.verbs += VerbVariant(GUN_SELECTOR_TOGGLE_VERB, action)
            }
        } else if (action is InputAction.SlotClick) {
            val count = action.item.gunAmmoConsumeCount(action.cursorItem)
            if (count > 0) {
                verbs += VerbVariant(
                    ItemVerb(
                        GUN_BARREL_AMMO_LOAD_VERB,
                        "Загрузить патроны в патронник",
                    ),
                    action
                )
            }
        }
    }
}

fun handleGunInteractions(player: EnginePlayer, isClient: Boolean = false) {
    player.handleInteraction(GUN_SHOOT_VERB.id) {
        val gun = handItem!!.require<Gun>()
        val barrel = gun.barrel

        if (gun.selector || timeElapsed % gun.rate != 0) return@handleInteraction
        val rotationVector = player.require<Orientation>().rotationVector
        val start = player.eyePos
        val shoot = GunShoot(start, rotationVector)
        if (barrel.bullets > 0) {
            barrel.bullets = (barrel.bullets - 1).coerceAtLeast(0)
            handItem.emitPlaySoundEvent(GUNFIRE_SOUND, EngineSoundCategory.NEUTRAL)
            val parameters = BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)
            handItem.set(Recoil(shoot, parameters))
            handItem.world.events<BulletFire>() += BulletFire(
                shoot,
                parameters,
                Smoke(gun.smoke ?: VEC3_ZERO, player.velocity)
            )
        } else {
            if (!gun.clicked) {
                handItem.emitPlaySoundEvent(CLICK_EMPTY_SOUND, EngineSoundCategory.NEUTRAL)
                gun.clicked = true
            }
            player.finishInteraction()
        }
    }

    player.handleInteraction(GUN_SELECTOR_TOGGLE_VERB.id) {
        val gun = handItem!!.require<Gun>()
        gun.selector = !gun.selector
        handItem.emitPlaySoundEvent(SELECTOR_TOGGLE_SOUND, EngineSoundCategory.NEUTRAL)
        player.finishInteraction()
    }

    player.handleInteraction(GUN_BARREL_AMMO_LOAD_VERB) {
        val gun = slotAction.item.require<Gun>()
        val barrel = gun.barrel

        val cursorItem = slotAction.cursorItem
        val count = slotAction.item.gunAmmoConsumeCount(slotAction.cursorItem)
        if (count > 0) {
            barrel.bullets = (barrel.bullets + count).coerceAtMost(barrel.maxBullets)
            gun.clicked = false
            slotAction.item.emitPlaySoundEvent(ROUND_BARREL_SOUND, EngineSoundCategory.NEUTRAL)
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