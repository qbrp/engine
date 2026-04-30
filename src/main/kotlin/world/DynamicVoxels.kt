package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.Networked
import org.lain.engine.util.math.Vec3

data class DynamicVoxel(val pos: VoxelPos) : Component

data class ChunkedPos(
    val pos: EngineChunkPos,
    val voxelPos: VoxelPos,
    val centerPos: Vec3
) : Component

fun World.setDynamicVoxel(pos: VoxelPos, networked: Boolean = false): EntityId {
    val entity = addEntity()
    entity.setDynamicVoxel(pos, networked)
    chunkStorage.requireChunk(pos).dynamicVoxels[pos] = entity
    return entity
}

context(access: World)
fun EntityId.setDynamicVoxel(pos: VoxelPos, networked: Boolean = false) {
    val centerPos = pos.toCenterPos()
    setComponent(DynamicVoxel(pos))
    setComponent(ChunkedPos(EngineChunkPos(pos), pos, centerPos))
    if (networked) setComponent(Networked)
    setComponent(Location(access, pos.toCenterPos()))
}