package org.lain.engine.player

import org.lain.engine.util.inject

interface RaycastProvider {
    fun isPlayerSeeOther(player: Player, seen: Player): Boolean
}

fun Player.canSee(player: Player): Boolean {
     val raycastProvider by inject<RaycastProvider>()
    return raycastProvider.isPlayerSeeOther(this, player)
}