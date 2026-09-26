package org.lain.engine.mc

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.lain.engine.world.ImmutableVoxelPos

object CommonMixin {
    fun onBlockItemPlaced(context: BlockPlaceContext, level: Level, blockPos: BlockPos, state: BlockState) {
        level.engineAccess()?.onBlockPlaced(context.player, blockPos, state, level)
    }

    fun onAirBlockPlaced(level: Level, pos: BlockPos) {
        val world = level.engineAccess()?.simulation?.worlds[level.engineId] ?: return
        world.chunkStorage.removeVoxel(ImmutableVoxelPos(pos.x, pos.y, pos.z))
        level.engineAccess()?.onBlockDestroyed(level, pos)
    }

    fun onBlockInteraction(player: Player, level: Level, blockPos: BlockPos): Boolean {
        return level.engineAccess()?.onBlockInteraction(player, level, blockPos) == true
    }

}
