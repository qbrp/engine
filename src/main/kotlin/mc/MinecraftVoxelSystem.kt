package org.lain.engine.mc

import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.BlockState
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.world.DynamicVoxel
import org.lain.engine.world.VoxelDoor
import org.lain.engine.world.VoxelMeta
import org.lain.engine.world.World

data class MinecraftBlockState(val blockState: BlockState) : Component, VoxelMeta {
    override val id: String
        get() = blockState.registryKey.idString

    override fun hasTag(id: String): Boolean {
        return blockState.`is`(blockTag(id))
    }
}

fun World.tickVoxelAdapterSystem(level: Level) = iterate<DynamicVoxel> { voxel, (pos) ->
    voxel.setComponent(
        MinecraftBlockState(level.getBlockState(pos.toBlockPos()))
    )
}

fun World.tickVoxelDoorSystem(level: Level) = iterate<DynamicVoxel, VoxelDoor, MinecraftBlockState> { voxel, (pos), door, (blockState) ->
    val block = blockState.block
    if (!blockState.`is`(BlockTags.DOORS) || block !is DoorBlock) {
        voxel.removeComponent<VoxelDoor>()
        return@iterate
    }

    val blockIsOpen = block.isOpen(blockState)
    if (door.open && !blockIsOpen) {
        block.setOpen(null, level, blockState, pos.toBlockPos(), true)
    } else if (!door.open && blockIsOpen) {
        block.setOpen(null, level, blockState, pos.toBlockPos(), false)
    }
}