package org.lain.engine.player

import org.lain.engine.util.inject

interface RaycastProvider {
    fun isPlayerSeeOther(player: EnginePlayer, seen: EnginePlayer): Boolean
}

fun EnginePlayer.canSee(player: EnginePlayer): Boolean {
     val raycastProvider by inject<RaycastProvider>()
    return raycastProvider.isPlayerSeeOther(this, player)
}