package org.lain.engine.player.interaction

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.getCount
import org.lain.engine.item.getName
import org.lain.engine.mc.displayNameMiniMessage
import org.lain.engine.player.DecrementItem
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.GiveItemEvent
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.extendArm
import org.lain.engine.player.handFree
import org.lain.engine.player.handItem
import org.lain.engine.player.selectedSlot
import org.lain.engine.player.serverNarration
import org.lain.engine.world.World

data class HailAction(val toPlayer: EnginePlayer) : Component

data class GiveAction(val toPlayer: EnginePlayer) : Component

fun World.tickSocialActionSystem() {
    iterate<PlayerComponent, HailAction> { e, (player), (toPlayer) ->
        toPlayer.serverNarration("${player.displayNameMiniMessage} окликнул вас!", 40, true)
        e.removeComponent<HailAction>()
    }

    iterate<PlayerComponent, GiveAction>() { e, (player), (toPlayer) ->
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
                toPlayer.serverNarration("$playerName передал вам $itemName", 60)
            } else {
                player.serverNarration("$raycastPlayerName не может принять предмет, так как его руки заняты", 160)
                failure = "Чтобы взять его, нужно освободить ведущую руку"
            }
        } else {
            player.serverNarration("$raycastPlayerName не принял предмет...", 120)
            failure = "Чтобы взять его, нужно выставить руку"
        }

        if (failure != null) {
            toPlayer.serverNarration("$playerName хочет передать предмет...", 120)
            toPlayer.serverNarration(failure, 120)
        }
        e.removeComponent<GiveAction>()
    }
}
