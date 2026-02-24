package org.lain.engine.player

import org.lain.engine.util.handle

const val SOCIAL_INTERACTION_DISTANCE = 15

val HAIL_VERB = PlayerVerb(
    VerbId("hail"),
    "Окликнуть",
    priority = 10
)

fun appendSocialVerbs(player: EnginePlayer) = player.handle<VerbLookup> {
    forAction<InputAction.Attack>() {
        HAIL_VERB.takeIf { raycastPlayer(player, SOCIAL_INTERACTION_DISTANCE) != null }
    }
}

fun handleSocialInteractions(player: EnginePlayer) {
    player.handleInteraction(HAIL_VERB) {
        raycastPlayer?.serverNarration(
            "${player.displayName} окликнул вас!",
            40
        )
        player.finishInteraction()
    }
}