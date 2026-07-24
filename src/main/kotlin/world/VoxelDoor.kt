package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate

@Serializable
data class VoxelDoor(var open: Boolean) : Component