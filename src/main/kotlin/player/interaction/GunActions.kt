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
import org.lain.engine.item.GunBarrelLoad
import org.lain.engine.item.GunMagazineLoad
import org.lain.engine.item.GunMagazineTakeOff
import org.lain.engine.item.GunMagazines
import org.lain.engine.item.GunModeToggle
import org.lain.engine.item.HoldsGunTrigger
import org.lain.engine.item.Item
import org.lain.engine.item.Magazine
import org.lain.engine.item.isGun
import org.lain.engine.player.PlayerComponent
import org.lain.engine.world.World


@Serializable
object ToggleGunModeCommand : Component {
    val VERB = Verb(
        id = VerbId("gun_toggle_mode"),
        name = "Сменить режим огня",
        holdsInput = InputAction.Base
    ) { ToggleGunModeCommand }
}

@Serializable
object LoadGunFromOffhandCommand : Component {
    val VERB = Verb(
        id = VerbId("barrel_ammo_load"),
        name = "Загрузить патроны в патронник",
        holdsInput = InputAction.Base
    ) { LoadGunFromOffhandCommand }
}

@Serializable
object HoldGunTriggerCommand : Component {
    val VERB = Verb(
        id = VerbId("shoot"),
        name = "Нажать на спусковой крючок",
        holdsInput = InputAction.Attack
    ) { HoldGunTriggerCommand }
}

@Serializable
object TakeOffMagazineCommand : Component {
    val VERB = Verb(
        id = VerbId("take_off_magazine"),
        name = "Снять магазин",
        holdsInput = InputAction.TakeOff
    ) { TakeOffMagazineCommand }
}

fun World.collectGunVerbs() = iterate<VerbLookup, Hand>() { _, lookup, hand ->
    val gunItem = hand.item?.takeIf { it.isGun() } ?: return@iterate

    if (InputAction.Attack in lookup.input) {
        lookup.verbs += HoldGunTriggerCommand.VERB
    }

    if (InputAction.TakeOff in lookup.input && gunItem.getComponent<GunMagazines>()?.base != null) {
        lookup.verbs += TakeOffMagazineCommand.VERB
    }

    if (InputAction.Base !in lookup.input) return@iterate

    val offhandItem = hand.opposite.requireComponent<Hand>().item
    val offhandItemId = offhandItem?.getComponent<Item>()?.id
    val canLoadMagazine =
        offhandItem?.hasComponent<Magazine>() == true && offhandItemId == gunItem.getComponent<GunMagazines>()?.supports
    val canLoadBarrel =
        offhandItemId != null &&
            offhandItemId == gunItem.getComponent<Barrel>()?.ammunition &&
            !gunItem.hasComponent<GunMagazines>()

    lookup.verbs += if (canLoadMagazine || canLoadBarrel) {
        LoadGunFromOffhandCommand.VERB
    } else {
        ToggleGunModeCommand.VERB
    }
}

fun World.tickGunActionSystem() {
    iterate<HoldGunTriggerCommand, Hand> { interactor, _, hand ->
        interactor.removeComponent<HoldGunTriggerCommand>()
        interactor.setComponent(UsingItem(hand.item ?: return@iterate))
        interactor.setComponent(HoldsGunTrigger)
    }

    iterate<HoldsGunTrigger, Hand> { interactor, _, hand ->
        if (interactor.removeComponent<InteractionInterrupt>() != null) {
            interactor.removeComponent<HoldsGunTrigger>()
            return@iterate
        }
    }

    iterate<ToggleGunModeCommand, Hand> { interactor, _, hand ->
        interactor.removeComponent<ToggleGunModeCommand>()
        val gunItem = hand.item?.takeIf { it.isGun() } ?: return@iterate
        val owner = hand.owner.getComponent<PlayerComponent>()?.obj ?: return@iterate
        gunItem.setComponent(GunModeToggle(owner))
    }

    iterate<TakeOffMagazineCommand, Hand> { interactor, _, hand ->
        interactor.removeComponent<TakeOffMagazineCommand>()
        val gunItem = hand.item?.takeIf { it.isGun() } ?: return@iterate
        val owner = hand.owner.getComponent<PlayerComponent>()?.obj ?: return@iterate
        gunItem.setComponent(GunMagazineTakeOff(owner))
    }

    iterate<LoadGunFromOffhandCommand, Hand> { interactor, _, hand ->
        interactor.removeComponent<LoadGunFromOffhandCommand>()
        val gunItem = hand.item ?: return@iterate
        val itemToLoad = hand.opposite.requireComponent<Hand>().item
        val itemToLoadId = itemToLoad?.requireComponent<Item>()?.id ?: return@iterate
        val player = hand.owner.requireComponent<PlayerComponent>().obj

        val isMagazine =
            itemToLoad.hasComponent<Magazine>() && itemToLoadId == gunItem.getComponent<GunMagazines>()?.supports
        val isBullet = itemToLoadId == gunItem.getComponent<Barrel>()?.ammunition
        when {
            isMagazine -> gunItem.setComponent(GunMagazineLoad(player, itemToLoad))
            isBullet -> gunItem.setComponent(GunBarrelLoad(player, itemToLoad))
            else -> return@iterate
        }
    }
}
