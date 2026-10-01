package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.getCount
import org.lain.engine.item.getName
import org.lain.engine.mc.displayNameMiniMessage
import org.lain.engine.player.DecrementItem
import org.lain.engine.player.GiveItemEvent
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.extendArm
import org.lain.engine.player.handFree
import org.lain.engine.player.handItem
import org.lain.engine.player.selectedSlot
import org.lain.engine.player.narration
import org.lain.engine.player.whoSee
import org.lain.engine.world.World

@Serializable
object HailAction : Component {
    val VERB = Verb(
        HAIL_VERB,
        10,
        InputAction.Attack
    ) { HailAction }
}

@Serializable
object GiveAction : Component {
    val VERB = Verb(
        GIVE_AWAY,
        10,
        InputAction.Base
    ) { GiveAction }
}

fun World.collectSocialVerbs() {
    iterate<VerbLookup, PlayerComponent, PlayerInventory> { _, lookup, (player), inventory ->
        val input = lookup.input
        if (InputAction.Attack !in input && InputAction.Base !in input) return@iterate

        val sightPlayer = player.whoSee(SOCIAL_INTERACTION_DISTANCE) ?: return@iterate
        if (InputAction.Attack in input) {
            lookup.verbs += HailAction.VERB
        }
        if (InputAction.Base in input && inventory.mainHandItem != null && player.extendArm) {
            lookup.verbs += GiveAction.VERB
        }
    }
}

fun World.tickSocialActionSystem() {
    iterate<PlayerComponent, HailAction> { e, (player), _ ->
        e.removeComponent<HailAction>()
        val toPlayer = player.whoSee() ?: return@iterate
        toPlayer.narration("${player.displayNameMiniMessage} окликнул вас!", 40, true)
    }

    iterate<PlayerComponent, GiveAction>() { e, (player), action ->
        e.removeComponent<GiveAction>()
        val toPlayer = player.whoSee() ?: return@iterate
        val handItem = player.handItem ?: return@iterate
        val playerName = player.displayNameMiniMessage
        val raycastPlayerName = toPlayer.displayNameMiniMessage
        val itemName = handItem.getName()
        var failure: String? = null
        if (toPlayer.extendArm) {
            if (toPlayer.handFree) {
                handItem.setComponent(DecrementItem(handItem.getCount()))
                emitEvent(
                    GiveItemEvent(toPlayer, handItem, toPlayer.selectedSlot)
                )
                println("Передан предмет $handItem")
                toPlayer.narration("$playerName передал вам $itemName", 60)
            } else {
                player.narration("$raycastPlayerName не может принять предмет, так как его руки заняты", 160)
                failure = "Чтобы взять его, нужно освободить ведущую руку"
            }
        } else {
            player.narration("$raycastPlayerName не принял предмет...", 120)
            failure = "Чтобы взять его, нужно выставить руку"
        }

        if (failure != null) {
            toPlayer.narration("$playerName хочет передать предмет...", 120)
            toPlayer.narration(failure, 120)
        }
    }
}
