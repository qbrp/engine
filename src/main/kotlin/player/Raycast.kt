package org.lain.engine.player

import org.lain.engine.player.interaction.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.util.inject
import org.lain.engine.world.VoxelPos

interface RaycastProvider {
    fun whoSee(player: EnginePlayer, distance: Int): EnginePlayer?
    fun canSee(player: EnginePlayer, voxelPos: VoxelPos): Boolean
}

fun EnginePlayer.whoSee(distance: Int = SOCIAL_INTERACTION_DISTANCE): EnginePlayer? {
     val raycastProvider by inject<RaycastProvider>()
    return raycastProvider.whoSee(this, distance)
}

fun EnginePlayer.canSee(pos: VoxelPos, isClient: Boolean = false): Boolean {
    val raycastProvider by inject<RaycastProvider>()
    return raycastProvider.canSee(this, pos)
}