package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.Barrel
import org.lain.engine.item.EngineItem
import org.lain.engine.item.GunBarrelLoad
import org.lain.engine.item.GunMagazineLoad
import org.lain.engine.item.GunMagazines
import org.lain.engine.item.GunModeToggle
import org.lain.engine.item.GunTriggerPressed
import org.lain.engine.item.Item
import org.lain.engine.item.Magazine
import org.lain.engine.item.WRITEABLE_OPEN_SOUND
import org.lain.engine.item.Writable
import org.lain.engine.item.WritableOpen
import org.lain.engine.item.emitPlaySoundEvent
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.handItem
import org.lain.engine.world.World

@Serializable
object GunModeToggleAction : Component

data class GunLoadAction(val gunItem: EngineItem, val item: EngineItem) : Component

@Serializable
object StartShootAction : Component

@Serializable
object StopShootAction : Component

data class Shooting(val mainHand: Boolean, val offHand: Boolean) : Component

fun World.tickGunActionSystem() {
    iterate<StartShootAction, Player> { entity, intent, (player) ->
        entity.setComponent(Shooting(mainHand = true, offHand = false))
        entity.removeComponent<StartShootAction>()
        entity.syncAction(intent)
    }

    iterate<StopShootAction, PlayerInventory> { entity, intent, inventory ->
        val shooting = entity.removeComponent<Shooting>()
        entity.removeComponent<StopShootAction>()
        if (shooting?.mainHand == true) inventory.mainHandItem?.removeComponent<GunTriggerPressed>()
        if (shooting?.offHand == true) inventory.offHandItem?.removeComponent<GunTriggerPressed>()
        entity.syncAction(intent)
    }

    iterate<Shooting, PlayerInventory> { entity, (byMainHand, byOffHand), inventory ->
        if (byMainHand) inventory.mainHandItem?.setComponent(GunTriggerPressed)
        if (byOffHand) inventory.offHandItem?.setComponent(GunTriggerPressed)
    }

    iterate<GunModeToggleAction, Player> { entity, intent, (player) ->
        val gunItem = player.handItem ?: return@iterate
        gunItem.setComponent(GunModeToggle)
        entity.syncAction(intent)
        entity.removeComponent<GunModeToggleAction>()
    }

    if (!isClient) {
        iterate<GunLoadAction, Player> { entity, (gunItem, loadItem), (player) ->
            val loadItemId = loadItem.requireComponent<Item>().id
            if (loadItem.hasComponent<Magazine>() && loadItemId == gunItem.getComponent<GunMagazines>()?.supports) {
                gunItem.setComponent(GunMagazineLoad(player, loadItem))
            } else if (loadItemId == gunItem.getComponent<Barrel>()?.ammunition) {
                gunItem.setComponent(GunBarrelLoad(player, loadItem))
            }
            entity.removeComponent<GunLoadAction>()
        }
    }
}

@Serializable
object WritableOpenAction : Component

fun World.tickWritableActionSystem() {
    iterate<WritableOpenAction, Player>() { entity, action, (player) ->
        val handItem = player.handItem ?: return@iterate
        entity.emitPlaySoundEvent(WRITEABLE_OPEN_SOUND)
        entity.setComponent(WritableOpen(handItem.getComponent<Writable>() ?: return@iterate))
        entity.removeComponent<WritableOpenAction>()
        entity.syncAction(action)
    }
}