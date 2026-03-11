package org.lain.engine.player

import org.lain.engine.util.inject
import org.lain.engine.world.VoxelPos

interface RaycastProvider {
    fun whoSee(player: EnginePlayer, distance: Int, isClient: Boolean): EnginePlayer?
    fun canSee(player: EnginePlayer, voxelPos: VoxelPos, isClient: Boolean): Boolean
}

fun EnginePlayer.whoSee(distance: Int, isClient: Boolean = false): EnginePlayer? {
     val raycastProvider by inject<RaycastProvider>()
    return raycastProvider.whoSee(this, distance, isClient)
}

fun EnginePlayer.canSee(pos: VoxelPos, isClient: Boolean = false): Boolean {
    val raycastProvider by inject<RaycastProvider>()
    return raycastProvider.canSee(this, pos, isClient)
}