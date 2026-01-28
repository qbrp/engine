package org.lain.engine.world

import net.minecraft.util.math.BlockPos

interface VoxelPos {
    val x: Int
    val y: Int
    val z: Int

    fun asLong(): Long
}

data class MutableVoxelPos(
    override var x: Int,
    override var y: Int,
    override var z: Int
) : VoxelPos {
    override fun asLong(): Long {
        return BlockPos.asLong(x, y, z)
    }

    companion object {
        fun fromLong(long: Long) = BlockPos.fromLong(long).let { MutableVoxelPos(it.x, it.y, it.z) }
    }
}

fun VoxelPos(x: Int, y: Int, z: Int): VoxelPos = MutableVoxelPos(x, y, z)