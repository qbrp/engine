package org.lain.engine.world

import org.lain.engine.util.math.Vec3

data class Decal(val x: Int, val y: Int, val contents: DecalContents)

sealed class DecalContents {
    data class Chip(val radius: Int) : DecalContents()
}

typealias Decals = List<Decal>

data class DecalsLayer(val directions: Map<Direction, Decals>)

enum class Direction(val index: Int, val normal: Vec3) {
    DOWN(1, Vec3(0f, -1f, 0f)),
    UP(2, Vec3(0f, 1f, 0f)),
    NORTH(3, Vec3(0f, 0f, -1f)),
    SOUTH(4, Vec3(0f, 0f, 1f)),
    WEST(5, Vec3(-1f, 0f, 0f)),
    EAST(6, Vec3(1f, 0f, 0f));

    companion object {
        fun fromIndex(i: Int) = when(i) {
            1 -> DOWN
            2 -> UP
            3 -> NORTH
            4 -> SOUTH
            5 -> WEST
            6 -> EAST
            else -> error("Invalid index $i")
        }
    }
}

data class BlockDecals(val version: Int, val layers: List<DecalsLayer>)