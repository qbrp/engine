package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.item.GunBarrelLoad
import org.lain.engine.item.GunModeToggle
import org.lain.engine.item.GunTriggerPress
import org.lain.engine.item.WRITEABLE_OPEN_SOUND
import org.lain.engine.item.Writable
import org.lain.engine.item.WritableOpen
import org.lain.engine.item.emitPlaySoundEvent
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.handItem
import org.lain.engine.world.World

@Serializable
object GunModeToggleAction : Action

data class GunBarrelAmoLoadAction(val gunItem: EngineItem, val ammoItem: EngineItem) : Action

@Serializable
object StartShootAction : Action

@Serializable
object StopShootAction : Action

data class Shooting(val mainHand: Boolean, val offHand: Boolean) : Component

fun World.tickGunActionSystem() {
    iterate<StartShootAction, Player> { entity, intent, (player) ->
        entity.setComponent(Shooting(mainHand = true, offHand = false))
        entity.removeComponent<StartShootAction>()
        entity.syncAction(intent)
    }

    iterate<StopShootAction> { entity, intent ->
        entity.removeComponent<Shooting>()
        entity.removeComponent<StopShootAction>()
        entity.syncAction(intent)
    }

    iterate<Shooting, PlayerInventory> { entity, (byMainHand, byOffHand), inventory ->
        if (byMainHand) inventory.mainHandItem?.setComponent(GunTriggerPress)
        if (byOffHand) inventory.offHandItem?.setComponent(GunTriggerPress)
    }

    iterate<GunModeToggleAction, Player> { entity, intent, (player) ->
        val gunItem = player.handItem ?: return@iterate
        gunItem.setComponent(GunModeToggle)
        entity.syncAction(intent)
        entity.removeComponent<GunModeToggleAction>()
    }

    iterate<GunBarrelAmoLoadAction, Player> { entity, (gunItem, ammoItem), (player) ->
        gunItem.setComponent(GunBarrelLoad(player, ammoItem))
        entity.removeComponent<GunBarrelAmoLoadAction>()
    }
}

@Serializable
object WritableOpenAction : Action

fun World.tickWritableActionSystem() {
    iterate<WritableOpenAction, Player>() { entity, action, (player) ->
        val handItem = player.handItem ?: return@iterate
        entity.emitPlaySoundEvent(WRITEABLE_OPEN_SOUND)
        entity.setComponent(WritableOpen(handItem.getComponent<Writable>() ?: return@iterate))
        entity.removeComponent<WritableOpenAction>()
        entity.syncAction(action)
    }
}