package org.lain.engine.world

import kotlinx.serialization.Serializable
import net.minecraft.util.math.BlockPos
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.floorToInt

interface VoxelPos {
    val x: Int
    val y: Int
    val z: Int

    fun toShortString(): String = "$x, $y, $z"
    fun asLong(): Long = BlockPos.asLong(x, y, z)
}

@Serializable
data class ImmutableVoxelPos(override val x: Int, override val y: Int, override val z: Int) : VoxelPos {
    constructor(pos: VoxelPos) : this(pos.x, pos.y, pos.z)

    override fun equals(other: Any?): Boolean {
        return other is VoxelPos && this.x == other.x && this.y == other.y && this.z == other.z
    }
}

@Serializable
data class MutableVoxelPos(
    override var x: Int,
    override var y: Int,
    override var z: Int
) : VoxelPos {
    override fun equals(other: Any?): Boolean {
        return other is VoxelPos && this.x == other.x && this.y == other.y && this.z == other.z
    }

    companion object {
        fun fromLong(long: Long) = BlockPos.fromLong(long).let { MutableVoxelPos(it.x, it.y, it.z) }
    }
}

fun VoxelPos(x: Int, y: Int, z: Int): VoxelPos = MutableVoxelPos(x, y, z)

fun VoxelPos(x: Float, y: Float, z: Float): MutableVoxelPos = MutableVoxelPos(
    floorToInt(x),
    floorToInt(y),
    floorToInt(z)
)

fun VoxelPos(pos: Pos) = VoxelPos(pos.x, pos.y, pos.z)