package org.lain.engine.player

import org.lain.engine.util.inject

interface RaycastProvider {
    fun whoSee(player: EnginePlayer, distance: Int, isClient: Boolean): EnginePlayer?
}

fun EnginePlayer.whoSee(distance: Int, isClient: Boolean = false): EnginePlayer? {
     val raycastProvider by inject<RaycastProvider>()
    return raycastProvider.whoSee(this, distance, isClient)
}