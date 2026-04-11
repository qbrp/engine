package org.lain.engine.world

interface VoxelMeta {
    val id: String
    fun hasTag(id: String): Boolean
}