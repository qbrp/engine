package org.lain.engine.player

import org.lain.engine.item.FireMode
import org.lain.engine.item.Gun
import org.lain.engine.item.count
import org.lain.engine.item.name
import org.lain.engine.util.component.get
import org.lain.engine.util.component.handle
import org.lain.engine.util.component.set
import org.lain.engine.util.text.displayNameMiniMessage

const val SOCIAL_INTERACTION_DISTANCE = 15

val HAIL_VERB = VerbType(
    "hail",
    "Окликнуть",
    priority = 10
)

val GIVE_AWAY = VerbType(
    "give_away",
    "Передать предмет",
    priority = -5
)

fun appendSocialVerbs(player: EnginePlayer) = player.handle<VerbLookup> {
    forAction<InputAction.Attack>() {
        HAIL_VERB.takeIf { raycastPlayerNotNull(player, SOCIAL_INTERACTION_DISTANCE) }
    }
    forAction<InputAction.Base>(override = true) {
        GIVE_AWAY.takeIf {
            val lookOnPlayer = raycastPlayerNotNull(player, SOCIAL_INTERACTION_DISTANCE)
            val gunSafety = (handItem?.get<Gun>()?.mode ?: FireMode.SELECTOR) == FireMode.SELECTOR
            handItem != null && player.extendArm && lookOnPlayer && gunSafety
        }
    }
}

fun handleSocialInteractions(player: EnginePlayer) {
    player.handleInteraction(HAIL_VERB) {
        raycastPlayer?.serverNarration("${player.displayNameMiniMessage} окликнул вас!", 40, true)
        player.completeInteraction()
    }
    player.handleInteraction(GIVE_AWAY) {
        val raycastPlayer = raycastPlayer ?: return@handleInteraction
        val handItem = handItem ?: return@handleInteraction
        val playerName = player.displayNameMiniMessage
        val raycastPlayerName = raycastPlayer.displayNameMiniMessage
        val itemName = handItem.name
        var failure: String? = null
        if (raycastPlayer.extendArm) {
            if (raycastPlayer.handFree) {
                player.set(DestroyItemSignal(handItem.uuid, handItem.count))
                raycastPlayer.set(MoveItemSignal(handItem.uuid, raycastPlayer.selectedSlot))
                raycastPlayer.serverNarration("$playerName передал вам $itemName", 60)
            } else {
                player.serverNarration("$raycastPlayerName не может принять предмет, так как его руки заняты", 160)
                failure = "Чтобы взять его, нужно освободить ведущую руку"
            }
        } else {
            player.serverNarration("$raycastPlayerName не принял предмет...", 120)
            failure = "Чтобы взять его, нужно выставить руку"
        }

        if (failure != null) {
            raycastPlayer.serverNarration("$playerName хочет передать предмет...", 120)
            raycastPlayer.serverNarration(failure, 120)
        }
    }
}