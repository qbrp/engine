package org.lain.engine.world

import kotlinx.serialization.Serializable
import net.minecraft.util.math.BlockPos

interface VoxelPos {
    val x: Int
    val y: Int
    val z: Int

    fun asLong(): Long = BlockPos.asLong(x, y, z)
}

@Serializable
data class ImmutableVoxelPos(override val x: Int, override val y: Int, override val z: Int) : VoxelPos

data class MutableVoxelPos(
    override var x: Int,
    override var y: Int,
    override var z: Int
) : VoxelPos {
    companion object {
        fun fromLong(long: Long) = BlockPos.fromLong(long).let { MutableVoxelPos(it.x, it.y, it.z) }
    }
}

fun VoxelPos(x: Int, y: Int, z: Int): VoxelPos = MutableVoxelPos(x, y, z)