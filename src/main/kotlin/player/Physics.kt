package org.lain.engine.player

import org.lain.cyberia.ecs.Component
import org.lain.engine.world.VoxelMeta

data class PlayerPhysics(
    var noClip: Boolean = false,
    val collides: MutableList<VoxelMeta> = mutableListOf()
) : Component