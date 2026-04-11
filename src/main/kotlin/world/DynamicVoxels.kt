package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.util.component.EntityId

data class DynamicVoxel(val pos: VoxelPos) : Component

fun World.setDynamicVoxel(pos: VoxelPos): EntityId {
    val entity = addEntity {
        setComponent(DynamicVoxel(pos))
    }
    chunkStorage.requireChunk(pos).dynamicVoxels[pos] = entity
    return entity
}