package org.lain.engine.world

import java.util.stream.Stream

interface VoxelMeta {
    val id: String
    val tags: Stream<VoxelTag>
    fun hasTag(id: VoxelTag): Boolean
}

@JvmInline
value class VoxelTag(val value: String)